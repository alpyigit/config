package com.example.unifiedconfigclient.controller;

import com.example.unifiedconfigclient.config.AppConfig;
import com.example.unifiedconfigclient.config.DatabaseConfig;
import com.example.unifiedconfigclient.config.SecurityConfig;
import com.example.unifiedconfigclient.config.ComplexConfig;
import com.example.unifiedconfigclient.service.ConfigDebugService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/config")
@RefreshScope
public class ConfigController {

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
    
    @Autowired
    private ConfigDebugService configDebugService;

    @Value("${app.name:Default App}")
    private String appName;

    @GetMapping("/app-info")
    public Map<String, Object> getAppInfo() {
        Map<String, Object> response = new HashMap<>();
        response.put("name", appConfig.getName());
        response.put("version", appConfig.getVersion());
        response.put("description", appConfig.getDescription());
        response.put("author", appConfig.getAuthor());
        response.put("environment", appConfig.getEnvironment());
        return response;
    }

    @GetMapping("/database-info")
    public Map<String, Object> getDatabaseInfo() {
        Map<String, Object> response = new HashMap<>();
        response.put("url", databaseConfig.getUrl());
        response.put("username", databaseConfig.getUsername());
        response.put("driverClassName", databaseConfig.getDriverClassName());
        response.put("connectionTimeout", databaseConfig.getConnectionTimeout());
        response.put("maximumPoolSize", databaseConfig.getMaximumPoolSize());
        // Note: Password is intentionally excluded for security
        response.put("passwordConfigured", databaseConfig.getPassword() != null && !databaseConfig.getPassword().isEmpty());
        return response;
    }

    @GetMapping("/security-info")
    public Map<String, Object> getSecurityInfo() {
        Map<String, Object> response = new HashMap<>();
        response.put("jwtExpiration", securityConfig.getExpiration());
        response.put("jwtRefreshExpiration", securityConfig.getRefreshExpiration());
        // Note: Secret is intentionally excluded for security
        response.put("jwtSecretConfigured", securityConfig.getSecret() != null && !securityConfig.getSecret().isEmpty());
        return response;
    }

    @GetMapping("/all-properties")
    public Map<String, Object> getAllProperties() {
        Map<String, Object> response = new HashMap<>();
        response.put("app", getAppInfo());
        response.put("database", getDatabaseInfo());
        response.put("security", getSecurityInfo());
        return response;
    }

    @GetMapping("/encrypted-validation")
    public Map<String, Object> getEncryptedValidation() {
        Map<String, Object> response = new HashMap<>();
        
        // Check if encrypted values are properly decrypted
        String dbPassword = databaseConfig.getPassword();
        String jwtSecret = securityConfig.getSecret();
        
        response.put("databasePasswordDecrypted", dbPassword != null && !dbPassword.startsWith("{cipher}"));
        response.put("jwtSecretDecrypted", jwtSecret != null && !jwtSecret.startsWith("{cipher}"));
        response.put("encryptionWorking", 
            (dbPassword != null && !dbPassword.startsWith("{cipher}")) &&
            (jwtSecret != null && !jwtSecret.startsWith("{cipher}"))
        );
        
        return response;
    }

    @GetMapping("/complex-company")
    public Map<String, Object> getComplexCompanyInfo() {
        Map<String, Object> response = new HashMap<>();
        
        if (complexConfig.getCompany() != null) {
            response.put("companyName", complexConfig.getCompany().getName());
            response.put("founded", complexConfig.getCompany().getFounded());
            
            if (complexConfig.getCompany().getHeadquarters() != null) {
                response.put("headquarters", complexConfig.getCompany().getHeadquarters());
            }
            
            if (complexConfig.getCompany().getDepartments() != null) {
                response.put("departmentCount", complexConfig.getCompany().getDepartments().size());
                response.put("departments", complexConfig.getCompany().getDepartments().stream()
                    .map(dept -> {
                        Map<String, Object> deptInfo = new HashMap<>();
                        deptInfo.put("name", dept.getName());
                        deptInfo.put("code", dept.getCode());
                        deptInfo.put("budget", dept.getBudget());
                        deptInfo.put("employeeCount", dept.getEmployees() != null ? dept.getEmployees().size() : 0);
                        return deptInfo;
                    })
                    .collect(Collectors.toList()));
            }
        }
        
        return response;
    }

    @GetMapping("/complex-employees")
    public Map<String, Object> getComplexEmployeesInfo() {
        Map<String, Object> response = new HashMap<>();
        
        if (complexConfig.getCompany() != null && complexConfig.getCompany().getDepartments() != null) {
            int totalEmployees = complexConfig.getCompany().getDepartments().stream()
                .mapToInt(dept -> dept.getEmployees() != null ? dept.getEmployees().size() : 0)
                .sum();
            
            response.put("totalEmployees", totalEmployees);
            
            // Get employees with their skills and projects
            Map<String, Object> employeeDetails = new HashMap<>();
            complexConfig.getCompany().getDepartments().forEach(dept -> {
                if (dept.getEmployees() != null) {
                    dept.getEmployees().forEach(emp -> {
                        Map<String, Object> empInfo = new HashMap<>();
                        empInfo.put("name", emp.getFirstName() + " " + emp.getLastName());
                        empInfo.put("position", emp.getPosition());
                        empInfo.put("department", dept.getName());
                        empInfo.put("skillCount", emp.getSkills() != null ? emp.getSkills().size() : 0);
                        empInfo.put("skills", emp.getSkills());
                        empInfo.put("projectCount", emp.getProjects() != null ? emp.getProjects().size() : 0);
                        empInfo.put("activeProjects", emp.getProjects() != null ? 
                            emp.getProjects().stream()
                                .filter(project -> "active".equals(project.getStatus()))
                                .map(project -> project.getName())
                                .collect(Collectors.toList()) : null);
                        employeeDetails.put(emp.getId().toString(), empInfo);
                    });
                }
            });
            
            response.put("employees", employeeDetails);
        }
        
        return response;
    }

    @GetMapping("/complex-modules")
    public Map<String, Object> getComplexModulesInfo() {
        Map<String, Object> response = new HashMap<>();
        
        if (complexConfig.getModules() != null) {
            response.put("moduleCount", complexConfig.getModules().size());
            response.put("modules", complexConfig.getModules().stream()
                .map(module -> {
                    Map<String, Object> moduleInfo = new HashMap<>();
                    moduleInfo.put("name", module.getName());
                    moduleInfo.put("enabled", module.getEnabled());
                    moduleInfo.put("version", module.getVersion());
                    moduleInfo.put("dependencyCount", module.getDependencies() != null ? module.getDependencies().size() : 0);
                    moduleInfo.put("dependencies", module.getDependencies());
                    moduleInfo.put("hasConfiguration", module.getConfiguration() != null && !module.getConfiguration().isEmpty());
                    return moduleInfo;
                })
                .collect(Collectors.toList()));
        }
        
        return response;
    }

    @GetMapping("/complex-api-routes")
    public Map<String, Object> getComplexApiRoutesInfo() {
        Map<String, Object> response = new HashMap<>();
        
        if (complexConfig.getApiGateway() != null && complexConfig.getApiGateway().getRoutes() != null) {
            response.put("routeCount", complexConfig.getApiGateway().getRoutes().size());
            response.put("routes", complexConfig.getApiGateway().getRoutes().stream()
                .map(route -> {
                    Map<String, Object> routeInfo = new HashMap<>();
                    routeInfo.put("id", route.getId());
                    routeInfo.put("uri", route.getUri());
                    routeInfo.put("predicateCount", route.getPredicates() != null ? route.getPredicates().size() : 0);
                    routeInfo.put("predicates", route.getPredicates());
                    routeInfo.put("filterCount", route.getFilters() != null ? route.getFilters().size() : 0);
                    routeInfo.put("metadata", route.getMetadata());
                    return routeInfo;
                })
                .collect(Collectors.toList()));
        }
        
        return response;
    }

    @GetMapping("/complex-all")
    public Map<String, Object> getAllComplexConfiguration() {
        Map<String, Object> response = new HashMap<>();
        response.put("company", getComplexCompanyInfo());
        response.put("employees", getComplexEmployeesInfo());
        response.put("modules", getComplexModulesInfo());
        response.put("apiRoutes", getComplexApiRoutesInfo());
        return response;
    }
    
    @GetMapping("/debug")
    public Map<String, Object> getDebugInfo() {
        return configDebugService.getDebugInfo();
    }
    
    @GetMapping("/debug/environment")
    public Map<String, Object> getEnvironmentDebug() {
        Map<String, Object> response = new HashMap<>();
        
        // Trigger detailed logging
        configDebugService.logAllEnvironmentProperties();
        
        // Return debug information
        response.put("message", "Environment properties have been logged to console. Check application logs.");
        response.put("debugInfo", configDebugService.getDebugInfo());
        
        return response;
    }
    
    @GetMapping("/debug/raw-values")
    public Map<String, String> getRawConfigurationValues() {
        Map<String, String> rawValues = new HashMap<>();
        
        // Get raw property values from environment
        String[] keyProperties = {
            "app.name", "app.version", "app.description", "app.author", "app.environment",
            "database.url", "database.username", "database.driver-class-name",
            "database.connection-timeout", "database.maximum-pool-size",
            "security.jwt.expiration", "security.jwt.refresh-expiration",
            "cache.redis.host", "cache.redis.port",
            "messaging.kafka.bootstrap-servers"
        };
        
        for (String prop : keyProperties) {
            String value = environment.getProperty(prop);
            if (value != null) {
                // Hide sensitive values
                if (prop.contains("password") || prop.contains("secret")) {
                    rawValues.put(prop, "[HIDDEN - " + value.length() + " chars]");
                } else {
                    rawValues.put(prop, value);
                }
            }
        }
        
        return rawValues;
    }
}