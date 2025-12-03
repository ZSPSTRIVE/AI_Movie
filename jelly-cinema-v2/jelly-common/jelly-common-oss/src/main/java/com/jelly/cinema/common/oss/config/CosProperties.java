package com.jelly.cinema.common.oss.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 腾讯云 COS 配置属性
 *
 * @author Jelly Cinema
 */
@Data
@ConfigurationProperties(prefix = "cos.client")
public class CosProperties {

    /**
     * 访问域名
     */
    private String host;

    /**
     * SecretId
     */
    private String secretId;

    /**
     * SecretKey
     */
    private String secretKey;

    /**
     * 地域
     */
    private String region;

    /**
     * 存储桶名称
     */
    private String bucket;

    /**
     * AppId
     */
    private String appId;
}
