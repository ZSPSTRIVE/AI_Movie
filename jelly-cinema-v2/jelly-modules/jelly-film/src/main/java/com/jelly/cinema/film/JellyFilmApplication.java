package com.jelly.cinema.film;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 电影服务启动类
 *
 * @author Jelly Cinema
 */
@SpringBootApplication(scanBasePackages = "com.jelly.cinema")
@EnableDiscoveryClient
@MapperScan("com.jelly.cinema.film.mapper")
public class JellyFilmApplication {

    public static void main(String[] args) {
        SpringApplication.run(JellyFilmApplication.class, args);
        System.out.println("==================================================");
        System.out.println("  Jelly Film Service Started Successfully!");
        System.out.println("==================================================");
    }
}
