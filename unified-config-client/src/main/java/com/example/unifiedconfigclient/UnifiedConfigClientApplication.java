package com.example.unifiedconfigclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
public class UnifiedConfigClientApplication {
    
    private static final Logger logger = LoggerFactory.getLogger(UnifiedConfigClientApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(UnifiedConfigClientApplication.class, args);
    }
    
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("\n" +
                "  ╔═══════════════════════════════════════════════════════════════╗\n" +
                "  ║                🎯 CONFIG CLIENT READY 🎯                     ║\n" +
                "  ╠═══════════════════════════════════════════════════════════════╣\n" +
                "  ║  📋 Configuration has been loaded and decrypted successfully! ║\n" +
                "  ║                                                               ║\n" +
                "  ║  🔧 Debug Endpoints Available:                               ║\n" +
                "  ║     • http://localhost:8080/api/config/debug                  ║\n" +
                "  ║     • http://localhost:8080/api/config/debug/environment      ║\n" +
                "  ║     • http://localhost:8080/api/config/debug/raw-values       ║\n" +
                "  ║                                                               ║\n" +
                "  ║  📊 Standard Endpoints:                                       ║\n" +
                "  ║     • http://localhost:8080/api/config/app-info               ║\n" +
                "  ║     • http://localhost:8080/api/config/database-info          ║\n" +
                "  ║     • http://localhost:8080/api/config/security-info          ║\n" +
                "  ║     • http://localhost:8080/api/config/encrypted-validation   ║\n" +
                "  ║                                                               ║\n" +
                "  ║  🔄 Refresh: POST http://localhost:8080/actuator/refresh      ║\n" +
                "  ╚═══════════════════════════════════════════════════════════════╝\n");
    }
}