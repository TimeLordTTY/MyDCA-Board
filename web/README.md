# 前端 Monorepo 使用说明（重要）

## 项目结构

前端项目分为三个部分，通过 **npm workspaces** 统一管理：

- **shared**：共享核心逻辑（TypeScript 库，封装 API、Store、类型等）
- **pc-app**：PC 端应用（Vue 3 + Element Plus）
- **mobile-app**：移动端应用（Vue 3 + Vant 4）

三个包共用一份根目录下的 `node_modules`，避免重复安装依赖。

## 依赖安装（只能在 web 根目录执行）

```bash
cd web
npm install
```

- **不要** 在 `shared/`、`pc-app/`、`mobile-app/` 子目录中单独执行 `npm install`。
- 如果在子目录运行 `npm install`，可能会装出多份 `vue` / `pinia`，重新触发 Pinia `_s` 报错。

## 一键构建（推荐）

```bash
cd web
build-web.bat
```

或在 PowerShell 中：

```powershell
cd web
.\build-web.bat
```

**脚本功能：**

- **[1/4]** 在 `web/` 根目录执行 `npm install`（workspace 模式，共享 `node_modules`）
- **[2/4]** 构建 shared：`npm -w @wealth-hub/shared run build`
- **[3/4]** 构建 PC 端：`npm -w wealth-hub-pc-app run build`
- **[4/4]** 构建 Mobile 端：`npm -w wealth-hub-mobile-app run build`
- **[5/5]** 汇总构建产物到统一目录：
  - PC 端 → `web/dist/wealth-hub`
  - Mobile 端 → `web/dist/wealth-hub-mobile`

部署时只需要把 `web/dist` 目录整体同步到服务器即可（例如 `/www/wwwroot/wealth-hub/frontend/dist`）。

## 本地开发

推荐使用项目根目录的 `start-dev.bat`：

```bash
cd <项目根目录>
start-dev.bat
```

脚本行为：

- 在 `web/` 执行一次 `npm install`（如有需要）
- 构建一次 shared（保证类型与运行时代码最新）
- 启动两个开发服务器：
  - PC 端：`http://localhost:3000/wealth-hub/`
  - Mobile 端：`http://localhost:3001/wealth-hub-mobile/`

## 与 Vue / Pinia 相关的关键约定（防止 `_s` 问题再出现）

1. **shared 包的依赖声明（`web/shared/package.json`）**
   - `vue`、`pinia` 必须放在 `peerDependencies` + `devDependencies`，**不能** 放在 `dependencies`。
   - 这样 shared 只声明「兼容哪个版本」，真正的 Vue/Pinia 只会在外层应用安装一份。

2. **shared 构建配置（`web/shared/vite.config.ts`）**
   - `build.rollupOptions.external` 必须包含：`['vue', 'axios', 'pinia']`。
   - 这样打出来的库不会把 Vue/Pinia 打进 bundle，而是使用应用端提供的实例。

3. **应用端 Vite 配置（`pc-app/vite.config.ts`、`mobile-app/vite.config.ts`）**
   - `resolve.dedupe` 必须包含：`['vue', 'pinia', 'vue-router']`。
   - 避免解析路径时拉出第二份 Vue/Pinia 实例。

4. **路由守卫中不要过早使用 Pinia Store**
   - Mobile 端的 `router.beforeEach` 已改为仅使用 `localStorage.getItem('token')` 进行鉴权。
   - 请不要在 `beforeEach` 中直接 `useUserStore()`，否则在 Pinia 实例尚未激活时容易出错。

只要以上约定保持不变，就不会再出现 Pinia `_s` 相关错误。

## Nginx 与打包目录的对应关系

- `build-web.bat` 输出：
  - PC 端：`web/dist/wealth-hub`
  - Mobile 端：`web/dist/wealth-hub-mobile`
- 建议服务器目录结构：
  - `/www/wwwroot/wealth-hub/frontend/dist/wealth-hub`
  - `/www/wwwroot/wealth-hub/frontend/dist/wealth-hub-mobile`

对应的 Nginx 配置示例见：`config/nginx/wealth-hub.conf`。
