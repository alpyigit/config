package com.example.unifiedconfigclient.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    
    private String name;
    private String version;
    private String description;
    private String author;
    private String environment;
    
    @PostConstruct
    public void debugConfiguration() {
        logger.info("=== APP CONFIG DEBUG ===");
        logger.info("App Name: {}", name);
        logger.info("App Version: {}", version);
        logger.info("App Description: {}", description);
        logger.info("App Author: {}", author);
        logger.info("App Environment: {}", environment);
        logger.info("========================");
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }
}