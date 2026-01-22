@echo off
setlocal enabledelayedexpansion

REM Set console code page to UTF-8
chcp 65001 >nul 2>&1

echo ========================================
echo Wealth Hub Backend - Build and Run
echo ========================================
echo.

REM Save root directory
set "ROOT_DIR=%~dp0"

REM Change to backend directory
cd /d "%ROOT_DIR%backend"

REM Build backend
echo [1/2] Building backend...
call mvn clean package -DskipTests -q
if !errorlevel! neq 0 (
    echo.
    echo Build failed! Please check the error above.
    pause
    exit /b 1
)
echo Build successful!
echo.

REM Find JAR file
set JAR_FILE=
for %%f in (target\*.jar) do (
    if not "%%~xf"==".original" (
        set JAR_FILE=%%f
    )
)

if not defined JAR_FILE (
    echo Error: JAR file not found in target folder!
    pause
    exit /b 1
)

REM Start application
echo [2/2] Starting application...
echo JAR: %JAR_FILE%
echo.

REM Start Java application with UTF-8 encoding
java -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dstdout.encoding=UTF-8 -jar "%JAR_FILE%"

pause
endlocal
