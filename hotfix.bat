@echo off
chcp 65001 >nul
echo ========================================
echo   WhisperChat 前端快速热更
echo   (仅适用于改 JS/CSS/HTML)
echo ========================================
echo.

set SERVER=175.178.56.39
set REMOTE_DIR=/opt/tomcat/webapps/v1_2026_5_30

echo 上传前端文件到服务器...
scp -r src\main\webapp\* root@%SERVER%:%REMOTE_DIR%/

if %errorlevel% neq 0 (
    echo ❌ 上传失败
    pause
    exit /b 1
)

echo ✅ 前端文件已更新（无需重启 Tomcat，刷新浏览器即可）
echo.
pause
