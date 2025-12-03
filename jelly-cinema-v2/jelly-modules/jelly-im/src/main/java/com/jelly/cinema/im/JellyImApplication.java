package com.jelly.cinema.im;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * IM 服务启动类
 *
 * @author Jelly Cinema
 */
@SpringBootApplication(scanBasePackages = "com.jelly.cinema")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.jelly.cinema.common.api.feign")
@MapperScan("com.jelly.cinema.im.mapper")
public class JellyImApplication {

    public static void main(String[] args) {
        SpringApplication.run(JellyImApplication.class, args);
        System.out.println("==================================================");
        System.out.println("  Jelly IM Service Started Successfully!");
        System.out.println("==================================================");
    }
}
