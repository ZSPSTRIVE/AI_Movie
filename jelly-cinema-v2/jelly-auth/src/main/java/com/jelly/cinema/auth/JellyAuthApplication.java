package com.jelly.cinema.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 认证服务启动类
 *
 * @author Jelly Cinema
 */
@SpringBootApplication(scanBasePackages = "com.jelly.cinema")
@EnableDiscoveryClient
@MapperScan("com.jelly.cinema.auth.mapper")
public class JellyAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(JellyAuthApplication.class, args);
        System.out.println("==================================================");
        System.out.println("  Jelly Auth Service Started Successfully!");
        System.out.println("==================================================");
    }
}
