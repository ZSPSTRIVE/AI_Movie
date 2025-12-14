# 果冻影院 2.0（jelly-auth）用户增长体系落地文档

## 0. 目标与边界

### 0.1 目标

在 **不引入新数据库、不拆分新服务** 的前提下，在现有 `jelly-auth`（用户中心）服务内落地三类“用户资产”能力：

- 签到（Check-in）：高频写、按月分片、O(1) 判断“今天签没签”。
- 积分（Points）：强一致资产，具备可审计流水，支持并发安全加减。
- 优惠券（Coupons）：高并发秒杀/兑换，抗超卖，核心链路 Redis 原子化，MySQL 异步落库。

### 0.2 边界与原则

- **不引入新数据库**：所有表落在现有 MySQL（本项目默认 `jelly_cinema`）。
- **不拆分新服务**：全部代码落在 `jelly-auth`。
- **可运维**：关键链路必须具备幂等、失败补偿思路、可观测（日志/错误码）。
- **可扩展**：Key 命名、Topic 命名、表设计支持后续运营活动扩展。

---

## 1. 总体架构

### 1.1 模块职责

- `jelly-auth`
  - 签到：Redis Bitmap 记录当月签到位图；签到成功后同步发放积分；可选异步归档。
  - 积分：MySQL 账户表 `user_point` + 流水表 `user_point_log`。
  - 优惠券：Redis Lua 预占库存 + 记录领取人；成功后扣积分；随后通过 RocketMQ 异步写 `user_coupon`。

### 1.2 依赖与约束

- Redis：`spring-data-redis`（当前工程已具备）。
- MyBatis-Plus：已启用（含乐观锁插件）。
- RocketMQ：当前工程在 `jelly-im` 已使用；本能力在 `jelly-auth` 增加同款依赖。

---

## 2. 数据库表结构（MySQL）

> 说明：项目默认数据库为 `jelly_cinema`（见 `docker-compose.yml` 与 `application.yml.example`）。如果你的线上环境拆分为 `jelly_auth` 也可，但需同步调整数据源配置。

### 2.1 用户积分表（核心资产表）

```sql
CREATE TABLE IF NOT EXISTS `user_point` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `points` int NOT NULL DEFAULT '0' COMMENT '当前积分余额',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户积分表';
```

### 2.2 积分流水表（用于对账和审计）

```sql
CREATE TABLE IF NOT EXISTS `user_point_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `type` tinyint NOT NULL COMMENT '类型: 1-签到, 2-发帖, 3-兑换',
  `amount` int NOT NULL COMMENT '变动金额(+/-)',
  `remark` varchar(128) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水表';
```

### 2.3 优惠券模板表（运营后台配置）

```sql
CREATE TABLE IF NOT EXISTS `coupon_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(64) NOT NULL COMMENT '优惠券名称',
  `total_count` int NOT NULL COMMENT '总发行量',
  `used_count` int NOT NULL DEFAULT '0' COMMENT '已领取数量',
  `points_required` int NOT NULL DEFAULT '0' COMMENT '兑换所需积分',
  `start_time` datetime NOT NULL,
  `end_time` datetime NOT NULL,
  `status` tinyint DEFAULT '1' COMMENT '1-有效 0-下架',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券模板';
```

### 2.4 用户优惠券关联表（用户资产）

```sql
CREATE TABLE IF NOT EXISTS `user_coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `template_id` bigint NOT NULL,
  `status` tinyint DEFAULT '0' COMMENT '0-未使用 1-已使用 2-已过期',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_template` (`user_id`,`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券表';
```

### 2.5 签到归档表（MySQL 异步归档）

```sql
CREATE TABLE IF NOT EXISTS `user_checkin_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `checkin_date` date NOT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`,`checkin_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户签到归档表';
```

---

## 3. Redis 设计

### 3.1 Key 命名规范

- **统一前缀**：`jelly:growth:`
- **分片维度**：签到按月，优惠券按模板ID。

### 3.2 Key 列表

| Key | 类型 | 说明 | TTL |
|---|---|---|---|
| `jelly:growth:sign:{userId}:{yyyyMM}` | Bitmap | 当月签到位图（day-1 为 offset） | 建议设置到次月月底（或不设） |
| `jelly:growth:coupon:stock:{templateId}` | String(int) | 优惠券 Redis 库存 | 与活动结束时间对齐 |
| `jelly:growth:coupon:users:{templateId}` | Set | 已领取用户集合 | 与活动结束时间对齐 |

---

## 4. RocketMQ 设计

### 4.1 Topic / Group

| 事件 | Topic | ConsumerGroup | Payload |
|---|---|---|---|
| 优惠券领取成功落库 | `GROWTH_COUPON_EXCHANGE` | `growth-coupon-group` | JSON（`userId`,`templateId`,`eventTime`） |
| 签到归档 | `GROWTH_CHECKIN_ARCHIVE` | `growth-checkin-group` | JSON（`userId`,`checkinDate`,`eventTime`） |

> 说明：工程内 Topic 命名风格为全大写下划线（参考 `IM_MSG_SEND`）。

---

## 5. 接口设计（面向前端的人机交互）

### 5.1 签到

- `POST /auth/growth/sign/checkin`
  - 行为：为当天置位签到位图；发放签到积分；发送归档消息（可选）。
  - 成功：`R.ok(true)`
  - 失败：
    - 已签到：`R.fail(4001, "今天已经签到过了")`

- `GET /auth/growth/sign/status`
  - 返回：
    - `signedToday`：是否今日已签
    - `continuousDays`：当月连续签到天数

### 5.2 积分

- `GET /auth/growth/points/balance`
  - 返回当前积分余额

- `GET /auth/growth/points/logs?page=1&size=20`
  - 返回积分流水（按时间倒序）

### 5.3 优惠券

- `GET /auth/growth/coupons/templates`
  - 返回可兑换模板列表（包含 `pointsRequired`,`startTime`,`endTime`,`status`）

- `POST /auth/growth/coupons/{templateId}/exchange`
  - 行为：Redis Lua 预占库存并记录用户；扣减积分；MQ 异步落库。
  - 错误码建议：
    - 重复领取：`4101`
    - 库存不足：`4102`
    - 积分不足：`4103`
    - 活动未开始/已结束：`4104`

- `GET /auth/growth/coupons/my`
  - 返回当前用户已领取优惠券列表

### 5.4 运营/管理端（同服务内，管理员权限）

- `POST /auth/growth/admin/coupons/templates`
  - 新建优惠券模板

- `POST /auth/growth/admin/coupons/templates/{id}/publish`
  - 将模板库存装载到 Redis（用于秒杀链路）

---

## 6. 并发安全与一致性设计

### 6.1 签到（Redis Bitmap）

- 使用 `SETBIT` 的返回值作为“是否首次签到”的原子判定。
- 积分发放在 MySQL 事务内，流水与余额保持 ACID。
- 归档通过 MQ 异步写 `user_checkin_log`，使用唯一索引实现幂等。

### 6.2 积分（账户 + 流水）

- 所有变更均写 `user_point_log`，与余额变更同事务。
- 余额更新采用 SQL 原子更新：
  - 增加：`points = points + amount`
  - 扣减：在 `WHERE points >= need` 条件下更新，避免扣成负数。

### 6.3 优惠券（Redis Lua + 补偿）

核心链路：

1) Redis Lua 预占（判重 + 判库存 + 扣库存 + 记录用户）
2) 扣积分（MySQL 事务）
3) 发送 MQ 异步落库（写 `user_coupon` 幂等）

补偿：

- 若 **扣积分失败**（积分不足/DB异常），必须执行“反向 Lua”回滚 Redis 预占（`INCR` + `SREM`）。
- 若 **MQ 发送失败**：降级为同步落库（仍需幂等）。

---

## 7. 工程落点（代码结构建议）

在 `jelly-auth` 内新增：

- `controller`
  - `GrowthSignController`
  - `GrowthPointController`
  - `GrowthCouponController`
  - `GrowthAdminCouponController`

- `domain/entity`
  - `UserPoint`,`UserPointLog`,`CouponTemplate`,`UserCoupon`,`UserCheckinLog`

- `mapper`
  - 对应 Mapper（MyBatis-Plus）

- `service`
  - `SignService`,`UserPointService`,`CouponService`

- `resources/lua`
  - `exchange_coupon.lua`（预占）
  - `revert_coupon.lua`（回滚）

---

## 8. 联调 / 自测用例

- 签到
  - 未签到 -> 签到成功 -> 再次签到返回已签到
  - 验证积分余额 +10，流水新增一条

- 积分并发
  - 并发 100 次加分：余额与流水条数一致
  - 扣减积分不足：返回错误码且余额不变，流水不落地

- 优惠券秒杀
  - 1000 并发抢 10 张：Redis 库存不为负；MySQL 最终落库 10 条以内；重复请求幂等
  - 积分不足：Redis 预占后必须回滚（库存回升，用户不在 Set）

---

## 9. 上线与回滚

- 上线顺序
  - 先上线 DB 表（SQL）
  - 再上线 `jelly-auth`（含 MQ topic 配置）
  - 管理端发布库存到 Redis

- 回滚
  - 停止入口（网关降级/限流）
  - 保留 MySQL 数据表（资产不可随意删）
  - Redis Key 可按前缀清理（仅在确认无需历史数据时）
