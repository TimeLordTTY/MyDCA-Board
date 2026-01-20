@echo off
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

echo ========================================
echo Wealth Hub System - Full Build Script
echo ========================================
echo.

REM Save root directory
set "ROOT_DIR=%~dp0"
cd /d "%ROOT_DIR%"

REM Build frontend
echo [1/3] Building frontend...
cd /d "%ROOT_DIR%web"
if not exist "build-web.bat" (
    echo Error: build-web.bat not found!
    cd /d "%ROOT_DIR%"
    exit /b 1
)
call build-web.bat
if !errorlevel! neq 0 (
    echo Frontend build failed!
    cd /d "%ROOT_DIR%"
    exit /b 1
)
cd /d "%ROOT_DIR%"

REM Build Java backend
echo.
echo [2/3] Building Java backend...
cd /d "%ROOT_DIR%backend"
if not exist "pom.xml" (
    echo Error: pom.xml not found!
    cd /d "%ROOT_DIR%"
    exit /b 1
)
call mvn clean package -DskipTests
if !errorlevel! neq 0 (
    echo Java backend build failed!
    cd /d "%ROOT_DIR%"
    exit /b 1
)
cd /d "%ROOT_DIR%"

REM Check Python scripts
echo.
echo [3/3] Checking Python scripts...
if exist "%ROOT_DIR%scripts\python" (
    echo Python scripts check passed (no compilation needed)
) else (
    echo Python scripts directory not found
)

echo.
echo ========================================
echo All projects built successfully!
echo ========================================
pause
endlocal