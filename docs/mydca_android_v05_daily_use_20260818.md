# MyDCA Android v0.5 日常可用化交付核验

核验日期：2026-09-26
目标仓库：`TimeLordTTY/MyDCA-Board`（分支 `v2`）
对应任务：`task-mydca-android-v05-daily-use-20260818`
业务实现提交：`dca789f3482430ac8a15d208d3765bb0ab56353b`（已在本地 `v2` 提交，本轮未推送）

## 版本信息

- `versionCode = 6`，`versionName = "0.5.0"`。
- APK 制品命名升级为 `MyDCA-Board-v0.5.0-<short-sha>.apk`，Artifact 名称前缀为 `mydca-android-v0.5.0-<sha>`。

## 实现范围

- 保留总览、账户、账户详情、流水与持仓的只读查看口径，未新增写接口。
- 总览、资产、今日待办、草稿箱统一改为“非破坏式刷新”：
  - 首次加载使用 `LoadingSection` 占位；
  - 已有数据时刷新只显示 `RefreshBar`（含“最近更新”时间，刷新中禁用重复点击），不再清空当前内容；
  - 刷新失败用 `NoticeBanner` 说明原因并保留上一次成功数据；
  - 仅首次加载失败才进入 `ErrorSection` 全量错误态。
- 草稿箱新增“手工记一笔”文本入口：复用既有 OCR 协程状态机（`OcrDraftCoordinator.startTextEntry`），跳过图片阶段直接进入“待复核文字”，解析与生成 DRAFT 仍必须由用户逐步点击。
- 草稿详情新增“解析信息复核”区块（`DraftReview`）：展示 DRAFT 状态、交易类型、金额、账户、备注、解析置信度和缺失字段。
- 草稿列表展示待人工确认数量与 `#id · 状态 · 类型 · 金额 · 账户` 摘要。
- 资产页支持按需读取 `GET /api/v2/mobile/accounts/{id}` 的服务端账户详情，不参与列表批量刷新。
- 保留 v0.4.1 的资金用途筛选状态（`AccountFundUsageFilter`）及其选中态恢复行为。
- 草稿账户选择改用统一的 `DraftAccountSelection` 规则，并显式列出被保护账户及不能选择的原因。

## 草稿与安全边界（未放松）

- 支付通知候选仍只是候选，最多生成 DRAFT。
- 不自动 preview、不自动 confirm、不静默创建正式账本分录；“手工记一笔”同样只生成候选与 DRAFT。
- 确认必须同时满足：当前草稿 `status=DRAFT`、预览 `draftId` 与草稿一致、`preview.confirmSupported=true`、表单无未保存修改、无进行中的保存/预览/确认，并且用户二次确认。
- 普通消费（EXPENSE）只允许后端标记为可支出的 SPENDABLE 叶子账户；父账户、RESERVED、INVESTABLE 与待分配账户保持保护。
- 前端规则只用于提示，最终安全边界仍由后端 preview / confirm 重新校验。

## 资金用途口径（沿用 v0.4）

- `SPENDABLE`：可用于日常消费。
- `RESERVED`：专款，不得用于日常消费。
- `INVESTABLE`：可投资资金，不得用于日常消费。
- 未设置用途的合格叶子账户归入待分配，不默认用于消费或投资。

## 测试结果

- `backend: mvn -B test`：通过，46 项测试，0 失败、0 错误、0 跳过。
- `android-app: gradlew.bat --no-daemon testDebugUnitTest`：通过，17 个测试类共 68 项测试，0 失败、0 错误、0 跳过。
- `android-app: gradlew.bat --no-daemon assembleDebug`：通过。
- `android-app: gradlew.bat --no-daemon lintDebug`：通过；仅 2 条既有 warning（`DataExtractionRules`、`ObsoleteSdkInt`），0 error，且都不由本次改动引入。
- Web 未修改，因此未单独执行 Web production build；Web 构建实际由 post-task 钩子执行。
- `scripts/post-task-compile-hook.ps1`：通过（后端 `mvn -DskipTests package` 与 Web `npm run build`）。
- 未连接生产数据库，未修改数据库 schema，未创建或确认真实草稿，未修改任何真实账本、余额、持仓或订单。

## 本地构建产物（本轮）

本轮工作进程的执行约束为“提交但不推送”，因此由本进程产出的证据只有本地构建产物。
推送由 owner 批准的 approved Codex auto-executor 在进程退出后完成：它会校验改动路径是否落在
`allowed_paths` 内，随后推送结果提交，并在 finalize 阶段把任务从 `agent-inbox/pending` 移入
`agent-inbox/in_progress`，写入 AiCore 报告与交付清单，再排队 Hermes 企业微信完成通知。

`Android test APK` 工作流由推送 `v2` 分支的 `android-app/**` 变更触发。推送成功后由执行器侧记录真实
Run ID 与 Artifact ID；本报告不对尚未产生的 Run/Artifact 做任何推测或伪造。

- 本地 `assembleDebug` 产物路径：`android-app/app/build/outputs/apk/debug/app-debug.apk`
- 文件名：`app-debug.apk`
- 大小：55,718,565 bytes
- SHA-256：`C8B3D341A968EB9FDB94DE5815FA9D2E1F75B72AAE1DA9EE8E5B65B693307D53`
- APK 不进入源码仓库（`.gitignore` 已忽略 `*.apk` 与 `build/`）。

### GitHub Actions 制品状态

- Workflow：Android test APK
- 本进程产生的 Run：NOT_PRODUCED（工作进程按指令不推送，工作流未在本进程内触发）
- 本进程产生的 Artifact ID / 名称：NOT_PRODUCED（同上）
- 结果提交推送后由 approved Codex auto-executor 触发工作流并记录真实 Run/Artifact 证据
- 最近一次历史 Run（由前序 Android 提交触发，仅作参考，不代表 v0.5.0）：`30243077286`，结果 success，触发分支 `v2`。

工作流在 CI 用一次性 debug 签名密钥重新打包，因此 CI 产出的 APK 与上文本地 APK 的 SHA-256 不会相同；
两者只能分别作为“本地构建成功”与“CI 构建成功”的独立证据，不能互相替代。

## 明确未实现

本版本没有实现 AI 自动资金分类、自动 preview、自动 confirm、自动正式入账，也没有提供直接修改余额、持仓、订单或执行交易的入口；未接入真实大模型。
