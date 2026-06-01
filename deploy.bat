@echo off
chcp 65001 >nul
echo ========================================
echo   WhisperChat 一键部署
echo ========================================
echo.

set SERVER=175.178.56.39
set REMOTE_WAR=/opt/tomcat/webapps/v1_2026_5_30.war
set WAR_FILE=target\v1_2026_5_30-1.0-SNAPSHOT.war

echo [1/4] 编译打包...
call .\mvnw.cmd clean package -DskipTests -q
if %errorlevel% neq 0 (
    echo ❌ 编译失败，请检查代码
    pause
    exit /b 1
)
echo ✅ 打包完成

echo.
echo [2/4] 停止远端 Tomcat...
ssh root@%SERVER% "/opt/tomcat/bin/shutdown.sh" 2>nul
echo ✅ 已停止

echo.
echo [3/4] 上传 WAR (%WAR_FILE%  →  %REMOTE_WAR%)...
scp %WAR_FILE% root@%SERVER%:%REMOTE_WAR%
if %errorlevel% neq 0 (
    echo ❌ 上传失败
    pause
    exit /b 1
)
echo ✅ 上传完成

echo.
echo [4/4] 启动远端 Tomcat...
ssh root@%SERVER% "/opt/tomcat/bin/startup.sh"
echo ✅ 已启动

echo.
echo ========================================
echo   部署完成！
echo   http://%SERVER%:8080/v1_2026_5_30/
echo ========================================
pause
