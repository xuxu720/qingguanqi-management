@echo off
chcp 65001 >nul

echo ============================================
echo   清管器作业管理系统 - 一键启动
echo ============================================
echo.
echo   后端:  http://localhost:8080
echo   文档:  http://localhost:8080/doc.html
echo   前端:  http://localhost:5173
echo.
echo   将在两个独立窗口中分别启动前后端
echo ============================================
echo.

REM ====== 检查 MySQL ======
sc query MySQL80 >nul 2>&1
if %errorlevel% neq 0 (
    echo [警告] MySQL80 服务未运行，正在启动...
    net start MySQL80 >nul 2>&1
    if %errorlevel% neq 0 (
        echo [错误] 无法启动 MySQL，请手动启动后重试
        pause
        exit /b 1
    )
    echo [OK] MySQL 已启动
) else (
    echo [OK] MySQL 已在运行
)

echo.
echo [启动] 正在启动后端服务...
start "清管器-后端" /D "%~dp0" cmd /c "%~dp0start-backend.bat"

REM 等待后端启动
echo [等待] 等待后端服务启动（约 20 秒）...
timeout /t 8 /nobreak >nul
echo [等待] 继续等待...
timeout /t 12 /nobreak >nul

echo.
echo [启动] 正在启动前端服务...
start "清管器-前端" /D "%~dp0" cmd /c "%~dp0start-frontend.bat"

echo.
echo ============================================
echo   启动完成！
echo.
echo   前端:  http://localhost:5173
echo   文档:  http://localhost:8080/doc.html
echo.
echo   关闭此窗口不会影响服务运行
echo   要停止服务，请关闭后端/前端的命令窗口
echo ============================================
echo.

pause
