package com.jelly.cinema.auth.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jelly.cinema.auth.domain.entity.UserPoint;
import com.jelly.cinema.auth.domain.entity.UserPointLog;
import com.jelly.cinema.auth.domain.vo.PointLogVO;
import com.jelly.cinema.auth.mapper.UserPointLogMapper;
import com.jelly.cinema.auth.mapper.UserPointMapper;
import com.jelly.cinema.auth.service.UserPointService;
import com.jelly.cinema.common.core.exception.ServiceException;
import com.jelly.cinema.common.core.domain.PageQuery;
import com.jelly.cinema.common.core.domain.PageResult;
import com.jelly.cinema.common.security.utils.LoginHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPointServiceImpl implements UserPointService {

    private final UserPointMapper userPointMapper;
    private final UserPointLogMapper userPointLogMapper;

    private static final String[] TYPE_NAMES = {"", "签到", "发帖", "兑换", "消费", "退还"};

    @Override
    public Integer getBalance() {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("请先登录");
        }
        UserPoint userPoint = userPointMapper.selectById(userId);
        return userPoint != null ? userPoint.getPoints() : 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addPoints(Long userId, int amount, int type, String remark) {
        UserPoint userPoint = userPointMapper.selectById(userId);
        if (userPoint == null) {
            userPoint = new UserPoint();
            userPoint.setUserId(userId);
            userPoint.setPoints(amount);
            userPoint.setVersion(0);
            userPointMapper.insert(userPoint);
        } else {
            userPointMapper.addPoints(userId, amount);
        }

        UserPointLog pointLog = new UserPointLog();
        pointLog.setUserId(userId);
        pointLog.setType(type);
        pointLog.setAmount(amount);
        pointLog.setRemark(remark);
        pointLog.setCreateTime(LocalDateTime.now());
        userPointLogMapper.insert(pointLog);

        log.info("积分增加: userId={}, amount={}, type={}, remark={}", userId, amount, type, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deductPoints(Long userId, int amount, int type, String remark) {
        int rows = userPointMapper.deductPoints(userId, amount);
        if (rows == 0) {
            return false;
        }

        UserPointLog pointLog = new UserPointLog();
        pointLog.setUserId(userId);
        pointLog.setType(type);
        pointLog.setAmount(-amount);
        pointLog.setRemark(remark);
        pointLog.setCreateTime(LocalDateTime.now());
        userPointLogMapper.insert(pointLog);

        log.info("积分扣减: userId={}, amount={}, type={}, remark={}", userId, amount, type, remark);
        return true;
    }

    @Override
    public PageResult<PointLogVO> getPointLogs(PageQuery query) {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("请先登录");
        }

        Page<UserPointLog> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<UserPointLog> wrapper = new LambdaQueryWrapper<UserPointLog>()
                .eq(UserPointLog::getUserId, userId)
                .orderByDesc(UserPointLog::getCreateTime);

        Page<UserPointLog> result = userPointLogMapper.selectPage(page, wrapper);

        List<PointLogVO> records = result.getRecords().stream().map(log -> {
            PointLogVO vo = BeanUtil.copyProperties(log, PointLogVO.class);
            vo.setTypeName(log.getType() != null && log.getType() < TYPE_NAMES.length
                    ? TYPE_NAMES[log.getType()] : "其他");
            return vo;
        }).collect(Collectors.toList());

        return new PageResult<>(records, result.getTotal());
    }
}
