package com.example.unifiedconfigclient.service;

import com.example.unifiedconfigclient.config.AppConfig;
import com.example.unifiedconfigclient.config.DatabaseConfig;
import com.example.unifiedconfigclient.config.SecurityConfig;
import com.example.unifiedconfigclient.config.ComplexConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import java.util.*;

@Service
public class ConfigDebugService {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigDebugService.class);
    
    @Autowired
    private AppConfig appConfig;
    
    @Autowired
    private DatabaseConfig databaseConfig;
    
    @Autowired
    private SecurityConfig securityConfig;
    
    @Autowired
    private ComplexConfig complexConfig;
    
    @Autowired
    private Environment environment;
    
    @PostConstruct
    public void initializeAndDebugAllConfigs() {
        logger.info("\n" +
                "==================================================\n" +
                "       🔧 CONFIGURATION DEBUG SUMMARY 🔧\n" +
                "==================================================");
        
        debugEnvironmentInfo();
        debugSpringProfiles();
        debugConfigSources();
        
        logger.info("==================================================\n");
    }
    
    private void debugEnvironmentInfo() {
        logger.info("📋 ENVIRONMENT INFORMATION:");
        logger.info("   ├─ Spring Application Name: {}", environment.getProperty("spring.application.name"));
        logger.info("   ├─ Config Server URI: {}", environment.getProperty("spring.cloud.config.uri"));
        logger.info("   ├─ Server Port: {}", environment.getProperty("server.port"));
        logger.info("   └─ Java Version: {}", System.getProperty("java.version"));
    }
    
    private void debugSpringProfiles() {
        String[] activeProfiles = environment.getActiveProfiles();
        String[] defaultProfiles = environment.getDefaultProfiles();
        
        logger.info("🎯 SPRING PROFILES:");
        logger.info("   ├─ Active Profiles: {}", Arrays.toString(activeProfiles));
        logger.info("   └─ Default Profiles: {}", Arrays.toString(defaultProfiles));
    }
    
    private void debugConfigSources() {
        logger.info("📦 CONFIGURATION SOURCES:");
        
        // Check for encrypted values detection
        Map<String, String> encryptedKeys = findEncryptedProperties();
        
        logger.info("   ├─ Total Encrypted Properties Found: {}", encryptedKeys.size());
        
        if (!encryptedKeys.isEmpty()) {
            logger.info("   ├─ Sample Encrypted Properties:");
            encryptedKeys.entrySet().stream()
                .limit(5)
                .forEach(entry -> 
                    logger.info("   │  ├─ {}: {} chars", entry.getKey(), entry.getValue().length())
                );
        }
        
        // Check key configuration areas
        debugKeyConfigurationAreas();
    }
    
    private void debugKeyConfigurationAreas() {
        logger.info("   └─ Key Configuration Areas:");
        logger.info("      ├─ App Config: {} properties loaded", countLoadedProperties(appConfig));
        logger.info("      ├─ Database Config: {} properties loaded", countLoadedProperties(databaseConfig));
        logger.info("      ├─ Security Config: {} properties loaded", countLoadedProperties(securityConfig));
        logger.info("      └─ Complex Config: {} properties loaded", countLoadedProperties(complexConfig));
    }
    
    private Map<String, String> findEncryptedProperties() {
        Map<String, String> encryptedProps = new HashMap<>();
        
        // Check some key properties for original encrypted values
        String[] propsToCheck = {
            "app.name", "app.description", "database.url", "database.password",
            "security.jwt.secret", "cache.redis.host"
        };
        
        for (String prop : propsToCheck) {
            String value = environment.getProperty(prop);
            if (value != null && !value.trim().isEmpty()) {
                encryptedProps.put(prop, value);
            }
        }
        
        return encryptedProps;
    }
    
    private int countLoadedProperties(Object configObject) {
        if (configObject == null) return 0;
        
        try {
            // Use reflection to count non-null fields
            return (int) Arrays.stream(configObject.getClass().getDeclaredFields())
                .filter(field -> {
                    try {
                        field.setAccessible(true);
                        Object value = field.get(configObject);
                        return value != null && !value.toString().trim().isEmpty();
                    } catch (Exception e) {
                        return false;
                    }
                })
                .count();
        } catch (Exception e) {
            return 0;
        }
    }
    
    public Map<String, Object> getDebugInfo() {
        Map<String, Object> debugInfo = new HashMap<>();
        
        // App Configuration
        Map<String, Object> appInfo = new HashMap<>();
        appInfo.put("name", appConfig.getName());
        appInfo.put("version", appConfig.getVersion());
        appInfo.put("description", appConfig.getDescription());
        appInfo.put("author", appConfig.getAuthor());
        appInfo.put("environment", appConfig.getEnvironment());
        
        // Database Configuration (with password safety)
        Map<String, Object> dbInfo = new HashMap<>();
        dbInfo.put("url", databaseConfig.getUrl());
        dbInfo.put("username", databaseConfig.getUsername());
        dbInfo.put("passwordConfigured", databaseConfig.getPassword() != null);
        dbInfo.put("driverClassName", databaseConfig.getDriverClassName());
        dbInfo.put("connectionTimeout", databaseConfig.getConnectionTimeout());
        dbInfo.put("maximumPoolSize", databaseConfig.getMaximumPoolSize());
        
        // Security Configuration (with secret safety)
        Map<String, Object> securityInfo = new HashMap<>();
        securityInfo.put("secretConfigured", securityConfig.getSecret() != null);
        securityInfo.put("secretLength", securityConfig.getSecret() != null ? securityConfig.getSecret().length() : 0);
        securityInfo.put("expiration", securityConfig.getExpiration());
        securityInfo.put("refreshExpiration", securityConfig.getRefreshExpiration());
        
        debugInfo.put("app", appInfo);
        debugInfo.put("database", dbInfo);
        debugInfo.put("security", securityInfo);
        debugInfo.put("timestamp", new Date().toString());
        debugInfo.put("profiles", Arrays.asList(environment.getActiveProfiles()));
        
        return debugInfo;
    }
    
    public void logAllEnvironmentProperties() {
        logger.info("🔍 DETAILED ENVIRONMENT PROPERTIES DUMP:");
        
        // Get some key properties to show decryption working
        String[] keyProps = {
            "app.name", "app.version", "app.description", "app.author", "app.environment",
            "database.url", "database.username", "database.driver-class-name",
            "security.jwt.secret", "security.jwt.expiration",
            "cache.redis.host", "cache.redis.port",
            "messaging.kafka.bootstrap-servers"
        };
        
        for (String prop : keyProps) {
            String value = environment.getProperty(prop);
            if (value != null) {
                // Safe logging - hide sensitive data
                if (prop.contains("password") || prop.contains("secret")) {
                    logger.info("   ├─ {}: [CONFIGURED - {} chars]", prop, value.length());
                } else {
                    logger.info("   ├─ {}: {}", prop, value);
                }
            }
        }
    }
}