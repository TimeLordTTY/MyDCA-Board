@echo off
chcp 65001 >nul
REM 保存脚本所在目录（web目录）
set "WEB_DIR=%~dp0"
echo 开始编译前端项目...

REM 编译共享层
echo.
echo [1/3] 编译共享层...
cd shared
if not exist "node_modules" (
    echo 安装共享层依赖...
    call npm install
    if errorlevel 1 (
        echo 共享层依赖安装失败！
        cd ..
        exit /b 1
    )
)
call npm run build
if errorlevel 1 (
    echo 共享层编译失败！
    cd ..
    exit /b 1
)

REM 编译PC端
echo.
echo [2/3] 编译PC端...
cd ..\pc-app
if not exist "node_modules" (
    echo 安装PC端依赖...
    call npm install
    if errorlevel 1 (
        echo PC端依赖安装失败！
        cd ..
        exit /b 1
    )
)
call npm run build
if errorlevel 1 (
    echo PC端编译失败！
    cd ..
    exit /b 1
)

REM 编译Mobile端
echo.
echo [3/3] 编译Mobile端...
cd ..\mobile-app
if not exist "node_modules" (
    echo 安装Mobile端依赖...
    call npm install
    if errorlevel 1 (
        echo Mobile端依赖安装失败！
        cd ..
        exit /b 1
    )
)
call npm run build
if errorlevel 1 (
    echo Mobile端编译失败！
    cd ..
    exit /b 1
)

echo.
echo ✓ 所有前端项目编译完成！
echo.

REM 启动PC端开发服务器（新窗口）
echo 正在启动PC端开发服务器（端口 3000）...
start "PC端 - 财富中枢系统" cmd /k "cd /d %WEB_DIR%pc-app && npm run dev"

REM 等待1秒，确保第一个窗口启动
timeout /t 1 /nobreak >nul

REM 启动Mobile端开发服务器（新窗口）
echo 正在启动Mobile端开发服务器（端口 3001）...
start "Mobile端 - 财富中枢系统" cmd /k "cd /d %WEB_DIR%mobile-app && npm run dev"

echo.
echo ✓ 前端开发服务器已启动！
echo   - PC端: http://localhost:3000
echo   - Mobile端: http://localhost:3001
echo.

cd ..
