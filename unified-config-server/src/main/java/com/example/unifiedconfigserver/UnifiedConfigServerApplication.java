package com.example.unifiedconfigserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@SpringBootApplication
@EnableConfigServer
public class UnifiedConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(UnifiedConfigServerApplication.class, args);
    }
}