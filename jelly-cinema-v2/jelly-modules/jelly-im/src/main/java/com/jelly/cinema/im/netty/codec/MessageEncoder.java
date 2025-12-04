package com.jelly.cinema.im.netty.codec;

import com.jelly.cinema.im.netty.protocol.MessageProtocol;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息编码器
 * 
 * 将 MessageProtocol 对象编码为字节流
 * 
 * 协议格式：
 * +--------+--------+--------+--------+--------+--------+--------+--------+
 * | 魔数(4) | 版本(1) | 序列化(1)| 指令(1) | 状态(1) | 消息ID(8) | 长度(4) | 数据(N) |
 * +--------+--------+--------+--------+--------+--------+--------+--------+
 * 
 * @author Jelly Cinema
 */
@Slf4j
public class MessageEncoder extends MessageToByteEncoder<MessageProtocol> {

    @Override
    protected void encode(ChannelHandlerContext ctx, MessageProtocol msg, ByteBuf out) throws Exception {
        // 1. 魔数 (4 bytes)
        out.writeInt(MessageProtocol.MAGIC_NUMBER);
        
        // 2. 版本 (1 byte)
        out.writeByte(MessageProtocol.VERSION);
        
        // 3. 序列化类型 (1 byte)
        out.writeByte(msg.getSerializerType());
        
        // 4. 消息类型 (1 byte)
        out.writeByte(msg.getMessageType());
        
        // 5. 消息状态 (1 byte)
        out.writeByte(msg.getStatus());
        
        // 6. 消息 ID (8 bytes)
        out.writeLong(msg.getMessageId());
        
        // 7. 内容长度 (4 bytes)
        byte[] content = msg.getContent();
        if (content != null) {
            out.writeInt(content.length);
            // 8. 内容 (N bytes)
            out.writeBytes(content);
        } else {
            out.writeInt(0);
        }

        log.debug("消息编码完成: type={}, messageId={}, contentLength={}", 
                msg.getMessageType(), msg.getMessageId(), 
                content != null ? content.length : 0);
    }
}
