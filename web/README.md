# 前端项目编译说明

## 项目结构

前端项目分为三个部分：
- **shared** - 共享核心逻辑（TypeScript库）
- **pc-app** - PC端应用（Vue 3 + Element Plus）
- **mobile-app** - Mobile端应用（Vue 3 + Vant 4）

## 编译顺序

由于 `pc-app` 和 `mobile-app` 依赖 `shared`，需要先编译 `shared`，再编译应用端。

## 编译步骤

### 1. 安装依赖（首次运行）

```bash
# 安装共享层依赖
cd web/shared
npm install

# 安装PC端依赖
cd ../pc-app
npm install

# 安装Mobile端依赖
cd ../mobile-app
npm install
```

### 2. 编译共享层（必须先编译）

```bash
cd web/shared
npm run build
```

编译输出：`web/shared/dist/`

### 3. 编译PC端

```bash
cd web/pc-app
npm run build
```

编译输出：`web/pc-app/dist/`

### 4. 编译Mobile端

```bash
cd web/mobile-app
npm run build
```

编译输出：`web/mobile-app/dist/`

## 一键编译脚本

### Windows 批处理脚本（推荐）

使用 `web/build-web.bat` 一键编译所有前端项目：

```batch
cd web
build-web.bat
```

或者直接双击 `build-web.bat` 文件运行。

**脚本功能：**
- 自动检查并安装缺失的依赖（如果 `node_modules` 不存在）
- 按顺序编译：shared → pc-app → mobile-app
- 编译失败时自动停止并显示错误信息
- 支持中文输出

### Windows PowerShell

使用 `web/build-web.ps1` 一键编译所有前端项目：

```powershell
cd web
.\build-web.ps1
```

**脚本功能：**
- 自动检查并安装缺失的依赖（如果 `node_modules` 不存在）
- 按顺序编译：shared → pc-app → mobile-app
- 编译失败时自动停止并显示错误信息
- 彩色输出提示

## 开发模式

### 启动开发服务器

**共享层（监听模式）：**
```bash
cd web/shared
npm run dev
```

**PC端：**
```bash
cd web/pc-app
npm run dev
```
访问：http://localhost:3000

**Mobile端：**
```bash
cd web/mobile-app
npm run dev
```
访问：http://localhost:3001

## 类型检查

所有项目都支持TypeScript类型检查：

```bash
# 共享层
cd web/shared
npm run type-check

# PC端
cd web/pc-app
npm run type-check

# Mobile端
cd web/mobile-app
npm run type-check
```

## 注意事项

1. **依赖顺序**：必须先编译 `shared`，因为 `pc-app` 和 `mobile-app` 依赖它
2. **Node.js版本**：建议使用 Node.js 18+
3. **npm版本**：建议使用 npm 9+
4. **首次运行**：需要先执行 `npm install` 安装依赖
5. **共享层更新**：如果修改了 `shared` 的代码，需要重新编译 `shared`，然后重新编译应用端

## 常见问题

### 问题1：找不到 @wealth-hub/shared 模块

**解决方案**：
1. 确保已编译 `shared` 项目
2. 检查 `shared/dist/` 目录是否存在
3. 在 `pc-app` 或 `mobile-app` 中重新运行 `npm install`

### 问题2：TypeScript类型错误

**解决方案**：
运行类型检查命令查看详细错误：
```bash
npm run type-check
```

### 问题3：构建失败

**解决方案**：
1. 删除 `node_modules` 和 `package-lock.json`
2. 重新运行 `npm install`
3. 重新编译
