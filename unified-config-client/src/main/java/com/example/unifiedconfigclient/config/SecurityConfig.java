package com.example.unifiedconfigclient.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;

@Configuration
@ConfigurationProperties(prefix = "security.jwt")
public class SecurityConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);
    
    private String secret;
    private long expiration;
    private long refreshExpiration;
    
    @PostConstruct
    public void debugConfiguration() {
        logger.info("=== SECURITY CONFIG DEBUG ===");
        logger.info("JWT Secret: {}", secret != null ? "[CONFIGURED - " + secret.length() + " chars]" : "[NOT SET]");
        logger.info("JWT Expiration: {}", expiration);
        logger.info("JWT Refresh Expiration: {}", refreshExpiration);
        logger.info("==============================");
    }

    // Getters and Setters
    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpiration() {
        return expiration;
    }

    public void setExpiration(long expiration) {
        this.expiration = expiration;
    }

    public long getRefreshExpiration() {
        return refreshExpiration;
    }

    public void setRefreshExpiration(long refreshExpiration) {
        this.refreshExpiration = refreshExpiration;
    }
}