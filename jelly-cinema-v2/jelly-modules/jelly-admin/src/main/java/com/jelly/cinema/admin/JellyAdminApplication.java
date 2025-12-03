package com.jelly.cinema.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 后台管理服务启动类
 *
 * @author Jelly Cinema
 */
@SpringBootApplication(scanBasePackages = "com.jelly.cinema")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.jelly.cinema.admin.feign", "com.jelly.cinema.common.api.feign"})
@MapperScan("com.jelly.cinema.admin.mapper")
public class JellyAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(JellyAdminApplication.class, args);
        System.out.println("==================================================");
        System.out.println("  Jelly Admin Service Started Successfully!");
        System.out.println("==================================================");
    }
}
