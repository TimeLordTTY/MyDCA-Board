@echo off
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

REM Save script directory
set "WEB_DIR=%~dp0"
cd /d "%WEB_DIR%"
echo Starting frontend build...

REM Build shared layer
echo.
echo [1/3] Building shared layer...
cd /d "%WEB_DIR%shared"
if not exist "node_modules" (
    echo Installing shared layer dependencies...
    call npm install
    if !errorlevel! neq 0 (
        echo Shared layer dependency installation failed!
        cd /d "%WEB_DIR%"
        exit /b 1
    )
)
call npm run build
if !errorlevel! neq 0 (
    echo Shared layer build failed!
    cd /d "%WEB_DIR%"
    exit /b 1
)

REM Build PC app
echo.
echo [2/3] Building PC app...
cd /d "%WEB_DIR%pc-app"
if not exist "node_modules" (
    echo Installing PC app dependencies...
    call npm install
    if !errorlevel! neq 0 (
        echo PC app dependency installation failed!
        cd /d "%WEB_DIR%"
        exit /b 1
    )
)
call npm run build
if !errorlevel! neq 0 (
    echo PC app build failed!
    cd /d "%WEB_DIR%"
    exit /b 1
)

REM Build Mobile app (if exists)
echo.
echo [3/3] Building Mobile app...
if not exist "%WEB_DIR%mobile-app" (
    echo Skipping Mobile app (directory does not exist)
) else (
    cd /d "%WEB_DIR%mobile-app"
    if not exist "node_modules" (
        echo Installing Mobile app dependencies...
        call npm install
        if !errorlevel! neq 0 (
            echo Mobile app dependency installation failed!
            cd /d "%WEB_DIR%"
            exit /b 1
        )
    )
    call npm run build
    if !errorlevel! neq 0 (
        echo Mobile app build failed!
        cd /d "%WEB_DIR%"
        exit /b 1
    )
)

echo.
echo All frontend projects built successfully!
echo.

REM Start PC app dev server (new window)
echo Starting PC app dev server (port 3000)...
set "PC_APP_DIR=%WEB_DIR%pc-app"
start "PC App - Wealth Hub" cmd /k "cd /d %PC_APP_DIR% && npm run dev"

cd /d "%WEB_DIR%"
endlocal
