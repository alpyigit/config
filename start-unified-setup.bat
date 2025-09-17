@echo off
echo ============================================
echo         UNIFIED CONFIG SETUP
echo ============================================
echo.

echo This script starts:
echo 1. Unified Config Server (Port 8888)  
echo 2. Unified Config Client (Port 8080)
echo.

echo Starting Config Server...
start "Config Server" cmd /k "cd unified-config-server && mvn -f pom.xml org.springframework.boot:spring-boot-maven-plugin:run"

echo Waiting for Config Server to start...
timeout /t 15 /nobreak >nul

echo Starting Config Client...  
start "Config Client" cmd /k "cd unified-config-client && mvn -f pom.xml org.springframework.boot:spring-boot-maven-plugin:run"

echo.
echo ============================================
echo Both applications are starting...
echo.
echo Config Server: http://localhost:8888
echo Config Client: http://localhost:8080
echo.
echo Wait a few moments for full startup, then test:
echo.
echo Test endpoints:
echo - http://localhost:8080/api/config/app-info
echo - http://localhost:8080/api/config/database-info  
echo - http://localhost:8080/api/config/security-info
echo - http://localhost:8080/api/config/encrypted-validation
echo.
echo ============================================
pause