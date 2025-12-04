package com.jelly.cinema.im.netty.handler;

import com.jelly.cinema.im.netty.protocol.MessageProtocol;
import com.jelly.cinema.im.netty.session.SessionManager;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 心跳处理器
 * 
 * 功能：
 * 1. 处理客户端心跳请求 (PING)，回复心跳响应 (PONG)
 * 2. 处理空闲超时事件
 * 3. 检测连接健康状态
 * 
 * 心跳机制说明：
 * - 客户端需要定期发送心跳包（建议 30 秒一次）
 * - 服务端在读空闲超时后（60秒），认为连接不健康
 * - 连续 3 次空闲超时，关闭连接
 * 
 * @author Jelly Cinema
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ChannelHandler.Sharable
@ConditionalOnProperty(name = "netty.server.enabled", havingValue = "true", matchIfMissing = false)
public class HeartbeatHandler extends ChannelInboundHandlerAdapter {

    private final SessionManager sessionManager;

    /**
     * 最大空闲次数，超过则断开连接
     */
    private static final int MAX_IDLE_COUNT = 3;

    /**
     * 空闲计数 Key
     */
    private static final String IDLE_COUNT_KEY = "idleCount";

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof MessageProtocol protocol) {
            if (protocol.getMessageType() == MessageProtocol.MessageType.HEARTBEAT_REQUEST) {
                // 处理心跳请求
                handleHeartbeat(ctx, protocol);
                return;
            }
        }
        // 非心跳消息，传递给下一个处理器
        ctx.fireChannelRead(msg);
    }

    /**
     * 处理心跳请求
     */
    private void handleHeartbeat(ChannelHandlerContext ctx, MessageProtocol request) {
        // 1. 重置空闲计数
        ctx.channel().attr(io.netty.util.AttributeKey.<Integer>valueOf(IDLE_COUNT_KEY)).set(0);

        // 2. 更新最后心跳时间
        sessionManager.updateLastHeartbeat(ctx.channel());

        // 3. 构建心跳响应 (PONG)
        MessageProtocol response = MessageProtocol.builder()
                .messageType(MessageProtocol.MessageType.HEARTBEAT_RESPONSE)
                .messageId(request.getMessageId())
                .status(MessageProtocol.MessageStatus.SUCCESS)
                .serializerType(MessageProtocol.SerializerType.JSON)
                .build();

        // 4. 发送响应
        ctx.writeAndFlush(response);

        Long userId = sessionManager.getUserId(ctx.channel());
        log.debug("心跳响应: userId={}, messageId={}", userId, request.getMessageId());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent event) {
            handleIdleEvent(ctx, event);
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    /**
     * 处理空闲事件
     */
    private void handleIdleEvent(ChannelHandlerContext ctx, IdleStateEvent event) {
        Long userId = sessionManager.getUserId(ctx.channel());
        String channelId = ctx.channel().id().asShortText();

        if (event.state() == IdleState.READER_IDLE) {
            // 读空闲：客户端长时间没有发送消息
            Integer idleCount = ctx.channel().attr(
                    io.netty.util.AttributeKey.<Integer>valueOf(IDLE_COUNT_KEY)).get();
            if (idleCount == null) {
                idleCount = 0;
            }
            idleCount++;

            if (idleCount >= MAX_IDLE_COUNT) {
                log.warn("连接读空闲超时达到上限，关闭连接: userId={}, channelId={}, idleCount={}", 
                        userId, channelId, idleCount);
                ctx.close();
            } else {
                log.debug("连接读空闲: userId={}, channelId={}, idleCount={}/{}", 
                        userId, channelId, idleCount, MAX_IDLE_COUNT);
                ctx.channel().attr(
                        io.netty.util.AttributeKey.<Integer>valueOf(IDLE_COUNT_KEY)).set(idleCount);
            }

        } else if (event.state() == IdleState.WRITER_IDLE) {
            // 写空闲：服务端长时间没有发送消息
            // 可以主动发送心跳探测
            log.debug("连接写空闲: userId={}, channelId={}", userId, channelId);

        } else if (event.state() == IdleState.ALL_IDLE) {
            // 读写空闲
            log.debug("连接读写空闲: userId={}, channelId={}", userId, channelId);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Long userId = sessionManager.getUserId(ctx.channel());
        String channelId = ctx.channel().id().asShortText();
        
        log.info("连接断开: userId={}, channelId={}", userId, channelId);
        
        // 解绑会话
        sessionManager.unbind(ctx.channel());
        
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Long userId = sessionManager.getUserId(ctx.channel());
        String channelId = ctx.channel().id().asShortText();
        
        log.error("连接异常: userId={}, channelId={}, error={}", 
                userId, channelId, cause.getMessage());
        
        ctx.close();
    }
}
