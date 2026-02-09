@echo off
chcp 65001 >nul 2>&1

set "ROOT_DIR=%~dp0"

REM Install dependencies if needed
cd /d "%ROOT_DIR%web"
if not exist "node_modules" (
    echo Installing dependencies...
    call npm install
)

REM Build shared first (apps depend on it)
echo Building shared layer...
call npm -w @wealth-hub/shared run build

echo Starting PC app dev server (port 3000)...
start "PC App - Wealth Hub" /D "%ROOT_DIR%web" cmd /k "npm -w wealth-hub-pc-app run dev"

echo Starting Mobile app dev server (port 3001)...
start "Mobile App - Wealth Hub" /D "%ROOT_DIR%web" cmd /k "npm -w wealth-hub-mobile-app run dev"

echo.
echo Dev servers started in separate windows.
echo   PC:     http://localhost:3000/wealth-hub/
echo   Mobile: http://localhost:3001/wealth-hub-mobile/
echo.
