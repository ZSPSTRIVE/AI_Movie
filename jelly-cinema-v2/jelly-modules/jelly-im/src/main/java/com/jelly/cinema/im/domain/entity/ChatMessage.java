package com.jelly.cinema.im.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jelly.cinema.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 聊天消息实体
 *
 * @author Jelly Cinema
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_chat_msg")
public class ChatMessage extends BaseEntity {

    /**
     * 会话 ID (单聊: min_uid_max_uid, 群聊: group_id)
     */
    private String sessionId;

    /**
     * 发送者 ID
     */
    private Long fromId;

    /**
     * 接收者 ID (群聊为 GroupId)
     */
    private Long toId;

    /**
     * 消息类型：1-私聊，2-群聊
     */
    private Integer cmdType;

    /**
     * 内容类型：1-文本，2-图片，3-文件，4-语音
     */
    private Integer msgType;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 扩展字段 (JSON)
     */
    private String extra;

    /**
     * 消息序列号
     */
    private Long msgSeq;

    /**
     * 状态：0-正常，1-撤回
     */
    private Integer status;

    /**
     * 已读状态：0-未读，1-已读（仅私聊消息使用）
     */
    private Integer readStatus;

    // ========== Netty 模块兼容字段（不持久化）==========

    /**
     * 发送者 ID（Netty 模块使用，映射到 fromId）
     */
    @TableField(exist = false)
    private Long senderId;

    /**
     * 接收者 ID（Netty 模块使用，映射到 toId）
     */
    @TableField(exist = false)
    private Long receiverId;

    /**
     * 会话类型：1-私聊，2-群聊（Netty 模块使用，映射到 cmdType）
     */
    @TableField(exist = false)
    private Integer sessionType;

    /**
     * 内容类型（Netty 模块使用，映射到 msgType）
     */
    @TableField(exist = false)
    private Integer contentType;
}
