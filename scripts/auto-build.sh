#!/bin/bash
# ============================================================
# Claude Code Stop Hook - 自动编译脚本
# 检查 .needs-build 标记 -> 编译后端+前端 -> 成功响铃 / 失败退出码2
# ============================================================
set -euo pipefail

MARKER=".claude/.needs-build"
LOCK=".claude/.build-running"

# 没有标记文件，跳过（本回合没有修改代码）
if [ ! -f "$MARKER" ]; then
  exit 0
fi

# 防止并发构建
if [ -f "$LOCK" ]; then
  exit 0
fi
touch "$LOCK"

# 清理标记
rm -f "$MARKER"

BUILD_OK=true

# ---- 编译后端 (Maven) ----
if [ -f "backend/pom.xml" ]; then
  echo ""
  echo "╔══════════════════════════════════════╗"
  echo "║  编译后端 (Maven Spring Boot)       ║"
  echo "╚══════════════════════════════════════╝"
  cd backend
  if mvn -DskipTests package -q 2>&1; then
    echo "✅ 后端编译成功"
  else
    echo "❌ 后端编译失败"
    BUILD_OK=false
  fi
  cd ..
fi

# ---- 编译前端 (npm) ----
if [ -f "web/package.json" ] && [ "$BUILD_OK" = true ]; then
  echo ""
  echo "╔══════════════════════════════════════╗"
  echo "║  编译前端 (Vue3 + npm)              ║"
  echo "╚══════════════════════════════════════╝"
  cd web
  if [ ! -d "node_modules" ]; then
    echo "安装前端依赖..."
    npm install --silent 2>&1 || { echo "❌ npm install 失败"; BUILD_OK=false; }
  fi
  if [ "$BUILD_OK" = true ]; then
    if npm run build 2>&1; then
      echo "✅ 前端编译成功"
    else
      echo "❌ 前端编译失败"
      BUILD_OK=false
    fi
  fi
  cd ..
fi

rm -f "$LOCK"

if [ "$BUILD_OK" = true ]; then
  echo ""
  echo "══════════════════════════════════════════"
  echo "  🎉 全部编译通过！"
  echo "══════════════════════════════════════════"
  # 播放提示音 + 系统通知
  echo -e "\a"
  # Windows Toast 通知
  powershell -NoProfile -Command "
    Add-Type -AssemblyName System.Windows.Forms;
    \$n = New-Object System.Windows.Forms.NotifyIcon;
    \$n.Icon = [System.Drawing.SystemIcons]::Information;
    \$n.Visible = \$true;
    \$n.ShowBalloonTip(5000, '财富中枢系统 - 编译成功', '后端 Maven + 前端 Vue3 全部编译通过！', [System.Windows.Forms.ToolTipIcon]::Info);
    Start-Sleep -Seconds 2;
    \$n.Dispose()
  " 2>/dev/null || true
  exit 0
else
  echo ""
  echo "══════════════════════════════════════════"
  echo "  ❌ 编译失败，需修复错误"
  echo "══════════════════════════════════════════"
  # 退出码2 = 触发 asyncRewake, 错误输出将喂给 Claude
  exit 2
fi
