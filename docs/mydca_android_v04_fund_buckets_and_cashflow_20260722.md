# MyDCA Android v0.4 资金分区与现金流交付核验

核验日期：2026-07-27
业务结果提交：`4a5a877666bed8001c95b24e13a65e6b3dc036d8`

## 实现范围

- 复用 `/api/v2/mobile/overview`、`/accounts`、`/accounts/{id}`、`/transactions` 和 `/holdings`。
- 新增并复用 `/api/v2/mobile/cash-flow`，由后端返回统一现金流口径。
- 总览只聚合 REAL/CASH 叶子账户，区分 `SPENDABLE`、`RESERVED`、`INVESTABLE` 和待分配；父账户不重复计数。
- Android 展示资金分区、本月收入、日常支出、净现金流、投资流入/流出及账户父子信息。
- 草稿账户选择器只展示当前用户可见的真实叶子账户及用途、余额信息；父账户不可选。

## 资金与现金流口径

- `SPENDABLE`：可用于日常消费。
- `RESERVED`：专款，不得用于日常消费。
- `INVESTABLE`：可投资资金，不得用于日常消费。
- 未设置用途的合格叶子账户归入待分配，不默认用于消费或投资。
- 收入和日常支出使用后端账本统计结果；投资流入、投资流出单列；转账不混入收入和支出。

## 草稿安全规则

- 后端通过当前用户和家庭上下文查询可见真实账户，避免跨用户选择。
- 父账户因存在子账户而被拒绝。
- 普通消费仅允许 `SPENDABLE`；选择 `RESERVED`、`INVESTABLE` 或待分配账户时，错误信息会说明原因并提示应选择的账户类型。
- Android 的选择限制只用于前端提示，最终规则仍由后端 preview/confirm 守门。
- 支付通知候选仍只生成 DRAFT，不会自动 preview、confirm 或正式入账。

## 测试结果

- `backend: mvn test`：通过，46 项测试，0 失败、0 错误、0 跳过。
- `android-app: gradlew.bat --no-daemon testDebugUnitTest assembleDebug lintDebug`：通过；JVM 单元测试 47 项，0 失败、0 错误、0 跳过；assembleDebug 与 lintDebug 成功。
- Web 未修改，因此未重复执行 Web production build。
- 未连接生产数据库，未修改数据库 schema，未创建或确认真实草稿，未修改任何真实账本、余额、持仓或订单。

### 2026-07-27 当前分支复验

- 在 `v2` 的 `1fc8fba85bbd0dfe9bbc2458a7ba53e8a6c8f82f` 上重新执行 `backend: mvn test`：46 项测试全部通过。
- 重新执行 Android `testDebugUnitTest assembleDebug lintDebug`：构建成功。
- 执行 `scripts/post-task-compile-hook.ps1`：后端 package 与 Web production build 均通过。
- 通过 GitHub API 重新核验 Run `30071421897` 的 Artifact `8588186382`，状态仍为未过期，构建提交、大小和过期时间与下方记录一致。

### 2026-07-27 owner-approved 续跑复验

- 在 `v2` 的 `6d355bcf0f23b224ab70a6c40495fed275c42a3f` 上再次执行 `backend: mvn test`：46 项测试全部通过。
- 再次执行 Android `testDebugUnitTest assembleDebug lintDebug`：构建成功；强制完成钩子的后端 package 与 Web production build 均通过。
- 重新下载 Artifact `8588186382` 到仓库外临时目录，APK 实际 SHA-256 与 `SHA256SUMS.txt` 一致；Artifact 仍未过期。
- 公开 HTTPS 只读探测结果保持为财富中枢入口 HTTP 200、未公开的 `/actuator/health` HTTP 404；未连接生产数据库，也未执行登录后写入或真实账本操作。

### 2026-07-27 最终执行复验

- 在 `v2` 的 `e7c5b562c9c4d3c644f50b27ff2484a04214a27d` 上执行 `backend: mvn test`：46 项测试全部通过。
- 执行 Android `testDebugUnitTest assembleDebug lintDebug`：构建成功。
- 通过 GitHub API 实时核验 Run `30071421897` 仍为成功，Artifact `8588186382` 仍未过期，名称、构建提交、压缩包大小和过期时间均与本报告一致。
- 本次只复核既有业务实现和交付证据，未连接生产数据库，未执行真实资金操作，也未重复修改已完成的业务代码。

### 2026-07-27 当前 HEAD 交付复核

- 在 `v2` 的 `7f1a9d0586c7b2376af721e480882349483bea13` 上执行 `backend: mvn test`：46 项测试全部通过。
- 执行 Android `testDebugUnitTest assembleDebug lintDebug`：构建成功；执行强制完成钩子，后端 package 与 Web production build 均通过。
- 重新下载 Run `30071421897` 的 Artifact `8588186382`，APK 实际 SHA-256 与制品内 `SHA256SUMS.txt` 一致；Artifact 状态仍为未过期。
- 公开 HTTPS 只读探测结果为财富中枢入口 HTTP 200、未公开的 `/actuator/health` HTTP 404；未部署服务，未连接生产数据库，未执行登录后写入或任何真实资金操作。

### 2026-07-27 owner-approved 任务完成核验

- 在 `v2` 的 `8d336da2442e6559eb119be9e8a9e12aa99335b2` 上执行 `backend: mvn test`：46 项测试全部通过。
- 执行 Android `testDebugUnitTest assembleDebug lintDebug`：构建成功；执行强制完成钩子，后端 package 与 Web production build 均通过。
- GitHub Actions Run `30071421897` 仍为成功，Artifact `8588186382` 未过期；重新下载所得 APK 为 `MyDCA-Board-v0.4-4a5a8776.apk`，大小 55,685,797 bytes，SHA-256 与制品内 `SHA256SUMS.txt` 一致。
- 本轮未修改既有业务实现，未部署服务、未连接生产数据库、未执行真实资金操作，也未发送超出目标仓库 `allowed_paths` 的通知或归档变更。

## 部署与只读核验

- 本轮未修改 backend/web，因此未重新部署服务器。
- 2026-07-27 对 `GET https://www.timelordtty.cn/wealth-hub/` 做只读探测，返回 HTTP 200。
- `GET https://www.timelordtty.cn/actuator/health` 由公网 Nginx 返回 404；该路径未公开，不能据此认定后端异常。
- 本地未保存且未检索隔离测试账号凭据，因此没有伪造“登录后 live smoke 已通过”的结论，也未执行任何生产写入 smoke。

## Android Actions 制品

- Workflow：Android test APK
- Run：`30071421897`
- Run URL：<https://github.com/TimeLordTTY/MyDCA-Board/actions/runs/30071421897>
- 结果：success
- 构建提交：`4a5a877666bed8001c95b24e13a65e6b3dc036d8`
- Artifact ID：`8588186382`
- Artifact 名称：`mydca-android-v0.4-4a5a877666bed8001c95b24e13a65e6b3dc036d8`
- APK 文件名：`MyDCA-Board-v0.4-4a5a8776.apk`
- APK 大小：55,685,797 bytes
- APK SHA-256：`a7a64e7f51cf8ac5d84d5b7a7b3413ac091ea2673de469b921dca1d33011df7b`
- GitHub Artifact 压缩包大小：31,360,826 bytes
- 创建时间：2026-07-24T06:10:53Z
- 过期时间：2026-08-23T06:10:51Z
- 核验时状态：未过期
- BaseUrl 元数据：`https://www.timelordtty.cn/`

APK 已下载到仓库外的临时目录，并将实际文件 SHA-256 与制品内 `SHA256SUMS.txt` 逐字核对一致；APK 未进入源码仓库。

## 明确未实现

本版本没有实现 AI 自动资金分类、自动 preview、自动 confirm、自动正式入账，也没有提供直接修改余额、持仓、订单或执行交易的入口。
