@echo off
echo ============================================================
echo Starting Spring Boot Order Orchestrator Backend...
echo ============================================================
set "JAVA_HOME=%~dp0tools\jdk"
set "PATH=%JAVA_HOME%\bin;%~dp0tools\apache-maven-3.9.6\bin;%PATH%"

cd /d %~dp0backend
call mvn spring-boot:run
pause
