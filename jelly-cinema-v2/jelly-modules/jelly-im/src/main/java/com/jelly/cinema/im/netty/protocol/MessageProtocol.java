package com.jelly.cinema.im.netty.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 自定义消息协议
 * 
 * 协议格式：
 * +--------+--------+--------+--------+--------+--------+--------+--------+
 * | 魔数(4) | 版本(1) | 序列化(1)| 指令(1) | 状态(1) | 消息ID(8) | 长度(4) | 数据(N) |
 * +--------+--------+--------+--------+--------+--------+--------+--------+
 * 
 * @author Jelly Cinema
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageProtocol implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 魔数：用于快速识别协议
     */
    public static final int MAGIC_NUMBER = 0x4A454C59; // JELY

    /**
     * 协议版本
     */
    public static final byte VERSION = 1;

    /**
     * 消息类型
     */
    private byte messageType;

    /**
     * 消息状态
     */
    private byte status;

    /**
     * 消息 ID（用于 ACK 确认）
     */
    private long messageId;

    /**
     * 序列化类型
     */
    private byte serializerType;

    /**
     * 消息内容
     */
    private byte[] content;

    /**
     * 消息类型枚举
     */
    public interface MessageType {
        byte HEARTBEAT_REQUEST = 0;   // 心跳请求 (PING)
        byte HEARTBEAT_RESPONSE = 1;  // 心跳响应 (PONG)
        byte AUTH_REQUEST = 2;        // 认证请求
        byte AUTH_RESPONSE = 3;       // 认证响应
        byte CHAT_MESSAGE = 4;        // 聊天消息
        byte CHAT_ACK = 5;            // 聊天消息 ACK
        byte GROUP_MESSAGE = 6;       // 群聊消息
        byte GROUP_ACK = 7;           // 群聊消息 ACK
        byte SYSTEM_MESSAGE = 8;      // 系统消息
        byte OFFLINE_MESSAGE = 9;     // 离线消息推送
        byte READ_RECEIPT = 10;       // 已读回执
    }

    /**
     * 消息状态
     */
    public interface MessageStatus {
        byte SUCCESS = 0;       // 成功
        byte FAIL = 1;          // 失败
        byte SENDING = 2;       // 发送中
        byte DELIVERED = 3;     // 已送达
        byte READ = 4;          // 已读
        byte RECALLED = 5;      // 已撤回
    }

    /**
     * 序列化类型
     */
    public interface SerializerType {
        byte JSON = 0;          // JSON 序列化
        byte PROTOBUF = 1;      // Protobuf 序列化
    }
}
