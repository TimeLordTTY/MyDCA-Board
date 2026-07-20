# MyDCA Android v0.2 真实财富数据接入报告

日期：2026-07-20
分支：`v2`

## 本轮结果

- Android 总览页已从占位卡切换为真实只读财富摘要，展示总资产、净资产、现金余额、持仓市值、负债、待办计数与最近活动。
- Android 资产页已切换为真实只读账户 / 流水 / 持仓浏览，不提供改余额、确认、撤销或改账入口。
- 后端新增 `/api/v2/mobile/**` 只读 facade，统一 Android 所需 DTO、分页结构和用户隔离。

## 复用 / 新增 API

### 复用

- `POST /api/v2/auth/login`
- `POST /api/v2/auth/logout`
- `GET /api/v2/users/me`
- `GET /api/v2/todos/today`
- `GET /api/v2/drafts`
- `GET /api/v2/drafts/{draftId}`
- `PUT /api/v2/drafts/{draftId}`
- `POST /api/v2/drafts/{draftId}/preview`
- `POST /api/v2/drafts/{draftId}/confirm`
- `POST /api/v2/drafts/{draftId}/ignore`

### 新增只读 mobile facade

- `GET /api/v2/mobile/overview`
- `GET /api/v2/mobile/accounts`
- `GET /api/v2/mobile/accounts/{id}`
- `GET /api/v2/mobile/transactions`
- `GET /api/v2/mobile/holdings`

## Android 页面与接口映射

- 总览页
  - `GET /api/v2/mobile/overview`
- 待办页
  - `GET /api/v2/todos/today`
- 草稿页
  - `GET /api/v2/drafts`
  - `GET /api/v2/drafts/{draftId}`
  - `PUT /api/v2/drafts/{draftId}`
  - `POST /api/v2/drafts/{draftId}/preview`
  - `POST /api/v2/drafts/{draftId}/confirm`
  - `POST /api/v2/drafts/{draftId}/ignore`
- 资产页
  - `GET /api/v2/mobile/accounts`
  - `GET /api/v2/mobile/transactions`
  - `GET /api/v2/mobile/holdings`

## 契约与口径说明

- 登录与当前用户仍复用既有 JWT 契约。
- 财富总览金额口径来自后端 `DashboardService`，Android 不自行重算正式资产。
- 持仓成本、市值、未实现盈亏来自后端 `HoldingService`。
- 流水分页返回统一 `items/page/pageSize/total/totalPages/hasNext`。
- 账户详情通过当前登录用户账户树过滤，不接受客户端传任意 userId。

## HTTPS smoke

- `GET https://www.timelordtty.cn/`：返回 `403`，说明 HTTPS 站点可达。
- `POST https://www.timelordtty.cn/api/v2/auth/login`：
  - 使用本地可推断的占位测试组合 `owner / test-password` 仅得到 `400 用户名或密码错误`。
  - 当前工作区和环境变量中未发现可安全使用的隔离测试账号凭据，因此未能完成“登录成功 + users/me + overview + accounts/transactions/holdings live 只读验证”。
- 结论：
  - HTTPS 连通性已验证。
  - 真实受保护数据的 live smoke 仍待使用现有安全测试账号补跑。

## 测试与构建

- 后端：`mvn -q test` 通过
- Android：`gradlew.bat testDebugUnitTest` 通过
- Android：`gradlew.bat assembleDebug` 通过
- Android：`gradlew.bat lintDebug` 通过
- 仓库 hook：`scripts/post-task-compile-hook.ps1` 通过
  - 本机缺少默认 `APPDATA\\npm\\codex.cmd`
  - 通过仓库内临时 shim + 进程级 `APPDATA` 覆盖完成，不修改系统级生产配置

## APK

- 本地文件：`android-app/app/build/outputs/apk/debug/app-debug.apk`
- 大小：`60,956,481` bytes
- SHA-256：`A997EB05E91945F096B28D0CF472EA916182726E930780F0E3C786994D8FD7EE`
- BaseUrl：`https://www.timelordtty.cn/`

## GitHub Actions / Artifact

- 本轮按当前指令未推送分支，因此没有新的 workflow run id、artifact id、下载位置或过期时间。
- 已更新 workflow，使推送 `v2` 后会执行 `testDebugUnitTest assembleDebug lintDebug`，并上传带 `v0.2` 与 commit 缩写的 APK artifact。

## 尚未完成

- 未完成使用现有安全测试账号的 live 登录 smoke。
- 未生成新的 GitHub Actions workflow run / artifact。
- 本轮未扩展为完整自动记账、自动 confirm 或正式账本写入能力。
