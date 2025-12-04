package com.jelly.cinema.im.netty.codec;

import com.jelly.cinema.im.netty.protocol.MessageProtocol;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息解码器
 * 
 * 基于 LengthFieldBasedFrameDecoder 实现，解决 TCP 粘包/拆包问题
 * 
 * 协议格式：
 * +--------+--------+--------+--------+--------+--------+--------+--------+
 * | 魔数(4) | 版本(1) | 序列化(1)| 指令(1) | 状态(1) | 消息ID(8) | 长度(4) | 数据(N) |
 * +--------+--------+--------+--------+--------+--------+--------+--------+
 * 
 * 头部总长度：4 + 1 + 1 + 1 + 1 + 8 + 4 = 20 bytes
 * 长度字段偏移：16 bytes
 * 长度字段长度：4 bytes
 * 
 * @author Jelly Cinema
 */
@Slf4j
public class MessageDecoder extends LengthFieldBasedFrameDecoder {

    /**
     * 头部长度（不包含长度字段后的内容长度）
     */
    private static final int HEADER_LENGTH = 20;

    /**
     * 最大帧长度：10MB
     */
    private static final int MAX_FRAME_LENGTH = 10 * 1024 * 1024;

    /**
     * 长度字段偏移量：魔数(4) + 版本(1) + 序列化(1) + 类型(1) + 状态(1) + 消息ID(8) = 16
     */
    private static final int LENGTH_FIELD_OFFSET = 16;

    /**
     * 长度字段本身的长度
     */
    private static final int LENGTH_FIELD_LENGTH = 4;

    public MessageDecoder() {
        // maxFrameLength: 最大帧长度
        // lengthFieldOffset: 长度字段偏移
        // lengthFieldLength: 长度字段长度
        // lengthAdjustment: 长度调整值（长度字段后还有多少字节才是内容）
        // initialBytesToStrip: 跳过的字节数（不跳过，保留完整帧）
        super(MAX_FRAME_LENGTH, LENGTH_FIELD_OFFSET, LENGTH_FIELD_LENGTH, 0, 0);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        // 使用父类方法处理粘包/拆包
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            return null;
        }

        try {
            return decodeFrame(frame);
        } finally {
            frame.release();
        }
    }

    /**
     * 解析完整帧
     */
    private MessageProtocol decodeFrame(ByteBuf frame) {
        // 1. 读取魔数
        int magicNumber = frame.readInt();
        if (magicNumber != MessageProtocol.MAGIC_NUMBER) {
            log.error("无效的魔数: {}", Integer.toHexString(magicNumber));
            throw new IllegalArgumentException("Invalid magic number: " + Integer.toHexString(magicNumber));
        }

        // 2. 读取版本
        byte version = frame.readByte();
        if (version != MessageProtocol.VERSION) {
            log.warn("协议版本不匹配: expected={}, actual={}", MessageProtocol.VERSION, version);
        }

        // 3. 读取序列化类型
        byte serializerType = frame.readByte();

        // 4. 读取消息类型
        byte messageType = frame.readByte();

        // 5. 读取状态
        byte status = frame.readByte();

        // 6. 读取消息 ID
        long messageId = frame.readLong();

        // 7. 读取内容长度
        int contentLength = frame.readInt();

        // 8. 读取内容
        byte[] content = null;
        if (contentLength > 0) {
            content = new byte[contentLength];
            frame.readBytes(content);
        }

        MessageProtocol message = MessageProtocol.builder()
                .serializerType(serializerType)
                .messageType(messageType)
                .status(status)
                .messageId(messageId)
                .content(content)
                .build();

        log.debug("消息解码完成: type={}, messageId={}, contentLength={}", 
                messageType, messageId, contentLength);

        return message;
    }
}
