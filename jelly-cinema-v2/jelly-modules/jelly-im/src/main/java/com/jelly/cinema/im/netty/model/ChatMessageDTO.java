package com.jelly.cinema.im.netty.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 聊天消息 DTO
 * 
 * @author Jelly Cinema
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息 ID（全局唯一）
     */
    private Long messageId;

    /**
     * 发送者 ID
     */
    private Long senderId;

    /**
     * 接收者 ID（私聊）/ 群组 ID（群聊）
     */
    private Long receiverId;

    /**
     * 消息类型：1-文本，2-图片，3-语音，4-视频，5-文件
     */
    private Integer contentType;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 会话类型：1-私聊，2-群聊
     */
    private Integer sessionType;

    /**
     * 发送时间戳
     */
    private Long timestamp;

    /**
     * 客户端消息序列号（用于去重）
     */
    private String clientMsgNo;

    /**
     * 附加信息（JSON 格式）
     */
    private String extra;

    /**
     * 消息类型常量
     */
    public interface ContentType {
        int TEXT = 1;       // 文本
        int IMAGE = 2;      // 图片
        int VOICE = 3;      // 语音
        int VIDEO = 4;      // 视频
        int FILE = 5;       // 文件
        int LOCATION = 6;   // 位置
        int CUSTOM = 7;     // 自定义
    }

    /**
     * 会话类型常量
     */
    public interface SessionType {
        int PRIVATE = 1;    // 私聊
        int GROUP = 2;      // 群聊
    }
}
