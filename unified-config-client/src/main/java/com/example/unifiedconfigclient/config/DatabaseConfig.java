package com.example.unifiedconfigclient.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;

@Configuration
@ConfigurationProperties(prefix = "database")
public class DatabaseConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);
    
    private String url;
    private String username;
    private String password;
    private String driverClassName;
    private int connectionTimeout;
    private int maximumPoolSize;
    
    @PostConstruct
    public void debugConfiguration() {
        logger.info("=== DATABASE CONFIG DEBUG ===");
        logger.info("Database URL: {}", url);
        logger.info("Database Username: {}", username);
        logger.info("Database Password: {}", password != null ? "[CONFIGURED]" : "[NOT SET]");
        logger.info("Database Driver: {}", driverClassName);
        logger.info("Connection Timeout: {}", connectionTimeout);
        logger.info("Maximum Pool Size: {}", maximumPoolSize);
        logger.info("==============================");
    }

    // Getters and Setters
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public void setMaximumPoolSize(int maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
    }
}