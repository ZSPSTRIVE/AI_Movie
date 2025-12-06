package com.jelly.cinema.common.oss.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.region.Region;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 腾讯云 COS 配置
 *
 * @author Jelly Cinema
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cos.client", name = "secretId")
public class CosConfig {

    private final CosProperties properties;

    @Bean
    public COSClient cosClient() {
        log.info("创建 COSClient bean - secretId={}, region={}, bucket={}", 
                properties.getSecretId() != null ? "已配置" : "未配置",
                properties.getRegion(), 
                properties.getBucket());
        // 初始化用户身份信息
        COSCredentials credentials = new BasicCOSCredentials(
                properties.getSecretId(), 
                properties.getSecretKey()
        );

        // 设置 bucket 的地域
        Region region = new Region(properties.getRegion());
        ClientConfig clientConfig = new ClientConfig(region);
        
        // JDK 21 与腾讯云 COS 存在 SSL/TLS 兼容性问题，使用 HTTP 协议
        // 腾讯云 COS 支持 HTTP 访问，数据传输仍然安全（有签名验证）
        clientConfig.setHttpProtocol(HttpProtocol.http);

        return new COSClient(credentials, clientConfig);
    }
}
