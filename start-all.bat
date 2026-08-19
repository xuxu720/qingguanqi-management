@echo off

echo ============================================
echo   QingGuanQi Management System - Start All
echo ============================================
echo.
echo   Backend:   http://localhost:8080
echo   API Doc:   http://localhost:8080/doc.html
echo   Frontend:  http://localhost:5173
echo.
echo ============================================
echo.

echo [1/3] Checking MySQL service...
sc query MySQL80 >nul 2>&1
if %errorlevel% neq 0 (
    echo [WARN] MySQL80 not running, starting...
    net start MySQL80 >nul 2>&1
    if %errorlevel% neq 0 (
        echo [ERROR] Cannot start MySQL, please start manually
        pause
        exit /b 1
    )
    echo [OK] MySQL started
) else (
    echo [OK] MySQL is running
)

echo.
echo [2/3] Starting backend service...
start "Backend" /D "%~dp0" cmd /c "%~dp0start-backend.bat"

echo [WAIT] Waiting 20s for backend to start...
timeout /t 8 /nobreak >nul
timeout /t 12 /nobreak >nul

echo.
echo [3/3] Starting frontend service...
start "Frontend" /D "%~dp0" cmd /c "%~dp0start-frontend.bat"

echo.
echo ============================================
echo   All services started!
echo.
echo   Frontend:  http://localhost:5173
echo   API Doc:   http://localhost:8080/doc.html
echo.
echo   Close this window to keep services running.
echo   To stop, close the backend/frontend windows.
echo ============================================
echo.

pause
