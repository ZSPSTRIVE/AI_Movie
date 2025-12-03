package com.jelly.cinema.im.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户设置实体
 *
 * @author Jelly Cinema
 */
@Data
@TableName("t_user_setting")
public class UserSetting {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 开启消息通知：0-关闭, 1-开启
     */
    private Integer enableNotification;

    /**
     * 消息提示音：0-关闭, 1-开启
     */
    private Integer enableSound;

    /**
     * 显示在线状态：0-隐藏, 1-显示
     */
    private Integer showOnlineStatus;

    /**
     * 允许陌生人消息：0-拒绝, 1-允许
     */
    private Integer allowStrangerMsg;

    /**
     * Enter发送消息：0-换行, 1-发送
     */
    private Integer enterToSend;

    /**
     * 显示已读状态：0-隐藏, 1-显示
     */
    private Integer showReadStatus;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
