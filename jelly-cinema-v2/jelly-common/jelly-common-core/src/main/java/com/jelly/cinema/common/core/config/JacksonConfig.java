package com.jelly.cinema.common.core.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Jackson 配置
 * 将 Long 类型序列化为 String，避免 JavaScript 大数字精度丢失
 *
 * @author Jelly Cinema
 */
@Slf4j
@AutoConfiguration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        log.info("配置 Jackson Long -> String 序列化");
        return builder -> {
            // 将 Long 类型序列化为 String
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
        };
    }
}
