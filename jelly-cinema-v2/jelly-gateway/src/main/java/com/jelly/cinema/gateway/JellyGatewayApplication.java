package com.jelly.cinema.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 网关服务启动类
 *
 * @author Jelly Cinema
 */
@SpringBootApplication
@EnableDiscoveryClient
public class JellyGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(JellyGatewayApplication.class, args);
        System.out.println("==================================================");
        System.out.println("    _      _ _          ____ _                          ");
        System.out.println("   | | ___| | |_   _   / ___(_)_ __   ___ _ __ ___   __ _ ");
        System.out.println("   | |/ _ \\ | | | | | | |   | | '_ \\ / _ \\ '_ ` _ \\ / _` |");
        System.out.println("   | |  __/ | | |_| | | |___| | | | |  __/ | | | | | (_| |");
        System.out.println("  _/ |\\___|_|_|\\__, |  \\____|_|_| |_|\\___|_| |_| |_|\\__,_|");
        System.out.println(" |__/          |___/                                      ");
        System.out.println("==================================================");
        System.out.println("  Jelly Gateway Service Started Successfully!");
        System.out.println("==================================================");
    }
}
