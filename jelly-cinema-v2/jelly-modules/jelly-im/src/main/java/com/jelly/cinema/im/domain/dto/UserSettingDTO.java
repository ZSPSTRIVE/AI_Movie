package com.jelly.cinema.im.domain.dto;

import lombok.Data;

/**
 * 用户设置 DTO
 *
 * @author Jelly Cinema
 */
@Data
public class UserSettingDTO {

    /**
     * 开启消息通知
     */
    private Boolean enableNotification;

    /**
     * 消息提示音
     */
    private Boolean enableSound;

    /**
     * 显示在线状态
     */
    private Boolean showOnlineStatus;

    /**
     * 允许陌生人消息
     */
    private Boolean allowStrangerMsg;

    /**
     * Enter发送消息
     */
    private Boolean enterToSend;

    /**
     * 显示已读状态
     */
    private Boolean showReadStatus;
}
