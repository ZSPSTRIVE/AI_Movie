package com.jelly.cinema.common.oss.config;

import com.jelly.cinema.common.oss.service.OssService;
import com.jelly.cinema.common.oss.service.impl.CosServiceImpl;
import com.qcloud.cos.COSClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * OSS 自动配置
 *
 * @author Jelly Cinema
 */
@Slf4j
@AutoConfiguration
@Import(CosConfig.class)
@EnableConfigurationProperties(CosProperties.class)
public class OssAutoConfiguration {

    @Bean
    @ConditionalOnBean(COSClient.class)
    public OssService ossService(COSClient cosClient, CosProperties properties) {
        log.info("创建 OssService bean - COS 配置已启用");
        return new CosServiceImpl(cosClient, properties);
    }
}
