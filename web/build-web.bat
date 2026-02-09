@echo off
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

REM Save script directory
set "WEB_DIR=%~dp0"
cd /d "%WEB_DIR%"
echo Starting frontend build (npm workspaces)...

REM Install all dependencies (shared node_modules)
echo.
echo [1/4] Installing all dependencies...
call npm install
if !errorlevel! neq 0 (
    echo Dependency installation failed!
    exit /b 1
)
echo Dependencies installed successfully.

REM Build shared layer
echo.
echo [2/4] Building shared layer...
call npm -w @wealth-hub/shared run build
if !errorlevel! neq 0 (
    echo Shared layer build failed!
    exit /b 1
)

REM Build PC app
echo.
echo [3/4] Building PC app...
call npm -w wealth-hub-pc-app run build
if !errorlevel! neq 0 (
    echo PC app build failed!
    exit /b 1
)

REM Build Mobile app
echo.
echo [4/4] Building Mobile app...
call npm -w wealth-hub-mobile-app run build
if !errorlevel! neq 0 (
    echo Mobile app build failed!
    exit /b 1
)

REM Prepare unified dist directory: dist\wealth-hub (PC) and dist\wealth-hub-mobile (Mobile)
echo.
echo [5/5] Preparing unified dist directory...

REM Clean old dist
if exist "%WEB_DIR%dist" (
    echo Cleaning old dist directory...
    rmdir /S /Q "%WEB_DIR%dist"
)

mkdir "%WEB_DIR%dist" 2>nul
mkdir "%WEB_DIR%dist\wealth-hub" 2>nul
mkdir "%WEB_DIR%dist\wealth-hub-mobile" 2>nul

echo Copying PC app build to dist\wealth-hub...
xcopy /E /I /Y "%WEB_DIR%pc-app\dist\*" "%WEB_DIR%dist\wealth-hub\" >nul

echo Copying Mobile app build to dist\wealth-hub-mobile...
xcopy /E /I /Y "%WEB_DIR%mobile-app\dist\*" "%WEB_DIR%dist\wealth-hub-mobile\" >nul

echo.
echo ========================================
echo All frontend projects built successfully!
echo Unified dist output: %WEB_DIR%dist
echo ========================================
echo.

cd /d "%WEB_DIR%"
endlocal
