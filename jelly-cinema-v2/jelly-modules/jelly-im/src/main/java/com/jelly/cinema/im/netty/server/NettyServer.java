package com.jelly.cinema.im.netty.server;

import com.jelly.cinema.im.netty.codec.MessageDecoder;
import com.jelly.cinema.im.netty.codec.MessageEncoder;
import com.jelly.cinema.im.netty.handler.ChatMessageHandler;
import com.jelly.cinema.im.netty.handler.HeartbeatHandler;
import com.jelly.cinema.im.netty.session.SessionManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

/**
 * Netty 服务器
 * 
 * 功能：
 * 1. 提供高性能 TCP 长连接服务
 * 2. 支持心跳检测
 * 3. 消息可靠传输
 * 
 * 启用方式：在配置文件中设置 netty.server.enabled=true
 * 
 * @author Jelly Cinema
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "netty.server.enabled", havingValue = "true", matchIfMissing = false)
public class NettyServer {

    private final HeartbeatHandler heartbeatHandler;
    private final ChatMessageHandler chatMessageHandler;
    private final SessionManager sessionManager;

    @Value("${netty.server.port:9999}")
    private int port;

    @Value("${netty.server.boss-threads:1}")
    private int bossThreads;

    @Value("${netty.server.worker-threads:8}")
    private int workerThreads;

    /**
     * 读空闲超时（秒）
     */
    @Value("${netty.server.reader-idle-time:60}")
    private int readerIdleTime;

    /**
     * 写空闲超时（秒）
     */
    @Value("${netty.server.writer-idle-time:30}")
    private int writerIdleTime;

    /**
     * 读写空闲超时（秒）
     */
    @Value("${netty.server.all-idle-time:90}")
    private int allIdleTime;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    @PostConstruct
    public void start() {
        new Thread(this::startServer, "netty-server-starter").start();
    }

    /**
     * 启动 Netty 服务器
     */
    private void startServer() {
        log.info("===== 启动 Netty 服务器 =====");

        bossGroup = new NioEventLoopGroup(bossThreads);
        workerGroup = new NioEventLoopGroup(workerThreads);

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    // TCP 连接队列大小
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    // 开启 TCP 心跳机制
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    // 关闭 Nagle 算法，提高实时性
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    // 接收缓冲区大小
                    .childOption(ChannelOption.SO_RCVBUF, 65536)
                    // 发送缓冲区大小
                    .childOption(ChannelOption.SO_SNDBUF, 65536)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();

                            // 1. 空闲状态检测器
                            pipeline.addLast("idleStateHandler", new IdleStateHandler(
                                    readerIdleTime, writerIdleTime, allIdleTime, TimeUnit.SECONDS));

                            // 2. 消息解码器（处理粘包/拆包）
                            pipeline.addLast("messageDecoder", new MessageDecoder());

                            // 3. 消息编码器
                            pipeline.addLast("messageEncoder", new MessageEncoder());

                            // 4. 心跳处理器
                            pipeline.addLast("heartbeatHandler", heartbeatHandler);

                            // 5. 聊天消息处理器
                            pipeline.addLast("chatMessageHandler", chatMessageHandler);
                        }
                    });

            // 绑定端口并启动
            ChannelFuture future = bootstrap.bind(port).sync();
            serverChannel = future.channel();

            // 设置服务器地址（用于分布式场景）
            String serverAddress = InetAddress.getLocalHost().getHostAddress() + ":" + port;
            sessionManager.setServerAddress(serverAddress);

            log.info("===== Netty 服务器启动成功 =====");
            log.info("监听端口: {}", port);
            log.info("服务器地址: {}", serverAddress);
            log.info("Boss 线程数: {}", bossThreads);
            log.info("Worker 线程数: {}", workerThreads);
            log.info("读空闲超时: {} 秒", readerIdleTime);
            log.info("写空闲超时: {} 秒", writerIdleTime);

            // 等待服务器关闭
            serverChannel.closeFuture().sync();

        } catch (Exception e) {
            log.error("Netty 服务器启动失败", e);
        } finally {
            shutdown();
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("===== 关闭 Netty 服务器 =====");

        if (serverChannel != null) {
            serverChannel.close();
        }

        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }

        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }

        log.info("===== Netty 服务器已关闭 =====");
    }

    /**
     * 获取服务器是否启动
     */
    public boolean isRunning() {
        return serverChannel != null && serverChannel.isActive();
    }

    /**
     * 获取在线连接数
     */
    public int getOnlineCount() {
        return sessionManager.getLocalOnlineCount();
    }
}
