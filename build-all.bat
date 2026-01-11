@echo off
chcp 65001 >nul
echo ========================================
echo 财富中枢系统 - 全项目编译脚本
echo ========================================
echo.

REM 设置错误处理
setlocal enabledelayedexpansion

REM 编译前端项目
echo [1/3] 编译前端项目...
cd web
call build-web.bat
if errorlevel 1 (
    echo 前端编译失败！
    cd ..
    exit /b 1
)
cd ..

REM 编译Java后端
echo.
echo [2/3] 编译Java后端...
cd backend
call mvn clean compile -DskipTests
if errorlevel 1 (
    echo Java后端编译失败！
    cd ..
    exit /b 1
)
cd ..

REM 编译Python脚本（如果有）
echo.
echo [3/3] 检查Python脚本...
if exist "scripts\python" (
    echo Python脚本检查通过（暂不编译）
) else (
    echo 未找到Python脚本目录
)

echo.
echo ========================================
echo ✓ 所有项目编译完成！
echo ========================================
pause
