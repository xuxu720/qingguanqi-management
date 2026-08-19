@echo off
cd /d "%~dp0backend"
echo Starting backend service...
mvn spring-boot:run
