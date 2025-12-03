package com.jelly.cinema.community;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 社区服务启动类
 *
 * @author Jelly Cinema
 */
@SpringBootApplication(scanBasePackages = "com.jelly.cinema")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.jelly.cinema.common.api.feign")
@MapperScan("com.jelly.cinema.community.mapper")
public class JellyCommunityApplication {

    public static void main(String[] args) {
        SpringApplication.run(JellyCommunityApplication.class, args);
        System.out.println("==================================================");
        System.out.println("  Jelly Community Service Started Successfully!");
        System.out.println("==================================================");
    }
}
