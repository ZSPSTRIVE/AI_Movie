package com.jelly.cinema.ai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * AI 服务启动类
 *
 * @author Jelly Cinema
 */
@SpringBootApplication(scanBasePackages = "com.jelly.cinema")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.jelly.cinema.common.api.feign")
@MapperScan("com.jelly.cinema.ai.mapper")
public class JellyAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(JellyAiApplication.class, args);
        System.out.println("==================================================");
        System.out.println("  Jelly AI Service Started Successfully!");
        System.out.println("==================================================");
    }
}
