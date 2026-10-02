# 财富中枢系统（Wealth Hub / MyDCA-Board）

**作者**：timelordtty  
**当前事实同步**：2026-10-02

财富中枢是个人/家庭财富管理平台。当前工程已进入 Phase3，重点是在统一账本和草稿安全边界之上，提供 PC/Web 与 Android 原生端的日常资产查看、记账采集、人工确认、行情/指标和后续策略能力。

> 开始开发前先读：`AGENTS.md` → `AGENT.MD` → `docs/CURRENT_DEVELOPMENT_STATE.md` → `docs/DOCUMENT_INDEX.md`。

## 技术栈

- 后端：Java 17 + Spring Boot 3.2 + MyBatis + MySQL 8 + JWT。
- PC Web：Vue 3 + TypeScript + Element Plus。
- Mobile H5：Vue 3 + Vant，历史兼容。
- 主移动端：Android 原生 Kotlin + Jetpack Compose + Material 3。
- Python：行情、指标、调度等任务型工具。

## 当前版本与里程碑

- Android：`0.18.0 / versionCode 19`；CI APK 证据待推送后真实成功构建回填。
- v0.18 配置偏离与止盈观察：主人自定义目标、区间及收益阈值；PC 配置/手工评估，Android 只读查看，情景预览仅为数学假设。无交易、订单、占用、结算或正式账本写入。详见 `docs/mydca_v018_release_hardening_20261002.md`。
- v0.18 `backend/migrations/20261002_allocation_policy.sql` 仅提交、未部署；新安装择一使用 `backend/sql/initsql/allocation_policy.sql`，不得重复执行。
- v0.17 风险观察中心：PC 手工规则评估、去重提醒历史、已读/静默；Android 仅 GET 查看规则、提醒及分页历史。UNKNOWN 不视为解除，仅写观察元数据，无交易、订单、结算或正式账本写入口。详见 `docs/mydca_v017_release_hardening_20261002.md`。
- v0.17 两个 migration `backend/migrations/20261002_risk_watch.sql`、`backend/migrations/20261002_risk_alert_history.sql` 仅提交、未自动部署；新安装可择一使用对应 `backend/sql/initsql/` 脚本，不得重复执行。
- v0.16 研究闭环：owner/family 隔离的方案生命周期、不可变来源证据与独立参数草稿；PC 创建、编辑、归档及人工受控回测，Android 只读分页、详情和关联运行。复用 v0.15 compare/evidence，不创建订单、结算或账本。详见 `docs/mydca_v016_release_hardening_20261001.md`。
- v0.16 migration `backend/migrations/20261001_research_plan.sql` 仅提交、未自动部署；新安装可择一使用 `backend/sql/initsql/research_plan.sql`，不得重复执行。
- v0.15 回测历史、比较、研究候选、证据导出与每日财富雷达已实现；历史目录需部署方配置持久化与权限。
- v0.14 发布硬化：Android 最近回测加载失败可重试，PC 待结算列表和策略实验室区分加载失败与空数据；APK workflow 已同步 v0.14.0 制品名。验证记录见 `docs/mydca_v014_release_hardening_20260929.md`。
- v0.14.0 草稿历史、已忽略草稿显式恢复和已确认草稿复制入口已实现；事件表 migration 尚待授权部署，恢复和复制仍需重新预览与二次确认。
- v0.7 全局“记一笔”快速采集中心、v0.8.0 系统 Share Sheet 文本/单图快速采集、v0.9.0 桌面快速记账小组件、v0.10.0 TRANSFER 转账草稿闭环、v0.11.0 投资买入 / 申购草稿闭环、v0.12.0 投资卖出 / 赎回草稿闭环、v0.13.0 人工结算预览与二次确认闭环均已完成。
- 最新后端可靠性里程碑：v0.8 草稿强幂等，result commit `ea8b3618e25c648127c62750c307ae8af976dd56`。
- v0.8 migration 已进入 Git，但未自动部署到数据库。
- v0.10.0 转账草稿闭环已完成：文本候选识别 TRANSFER、双账户影响预览、主人二次确认后经 `QuickEntryService.quickTransfer` 生成一笔正式转账流水，重复确认不重复记账。
- v0.11.0 投资买入 / 申购草稿闭环已完成：BUY / SUBSCRIPTION 候选识别、真实产品 + 单一资金来源账户选择、只读资金影响预览、主人二次确认后复用现有 `OrderService` 创建系统内 PENDING 订单并生成付款账本（CASH CREDIT + RECEIVABLE DEBIT）；不自动结算、不生成最终持仓、不调用真实交易渠道。
- v0.12.0 投资卖出 / 赎回草稿闭环已完成：SELL / REDEMPTION 候选识别、真实产品 + 该产品真实持仓来源 + 份额 + 到账账户选择、只读可用份额预览（已扣除 PENDING 占用）、主人二次确认后复用现有 `OrderService` 仅创建系统内 PENDING 订单并登记 SOURCE / TARGET 资金线；确认阶段不生成任何账本流水、不改现金余额、不改持仓，不自动结算、不调用真实交易渠道。
- v0.13.0 人工结算预览与二次确认闭环已完成：PENDING 订单先经只读 preview（`POST /api/v2/settlements/preview`）展示现金 / 持仓 / 手续费影响与 fresh 令牌，主人二次确认后携带令牌 confirm（`POST /api/v2/settlements/confirm`）才生成内部 `settlement_confirm` 与账本 / 持仓影响。BUY / SUBSCRIPTION 结算清理 RECEIVABLE 并形成 POSITION / 关联账户与手续费、不重复扣下单现金；SELL / REDEMPTION 结算才产生 CASH / POSITION / FEE 影响。重复确认幂等、异常整体回滚，仍无任何外部交易能力。

## Phase3 当前能力

- 草稿表与 `/api/v2/drafts/**`。
- `/api/v2/ai/accounting/parse-text` 与 `draft-from-intent`。
- Android 真实登录、安全 Token、今日待办、草稿、账户/流水/持仓。
- 手工文本、图片 OCR、支付通知候选。
- 系统分享入口：Share Sheet 文本 / 单张图片只预填到现有人工采集流程。
- 桌面快速记账小组件：四个静态中文入口只打开既有页面，不联网、不读写账本、不自动记账。
- 转账草稿闭环：TRANSFER 候选解析、双账户（转出 / 转入）影响预览、二次确认后生成一笔正式转账流水。
- 投资草稿闭环：BUY / SUBSCRIPTION 候选解析、真实产品 + 单一资金账户选择、订单与 CASH / RECEIVABLE 资金影响预览、二次确认后才创建 PENDING 订单并生成付款账本。
- 投资卖出 / 赎回草稿闭环：SELL / REDEMPTION 候选解析、真实产品 + 持仓来源 + 份额 + 到账账户选择、可用份额只读预览、二次确认后只创建内部 PENDING 订单并登记份额占用，不生成账本、不改现金与持仓。
- 人工结算预览与二次确认闭环：PENDING 订单（BUY / SUBSCRIPTION / SELL / REDEMPTION）只读预览现金 / 持仓 / 手续费影响与 fresh 令牌，主人二次确认后才生成内部结算确认与账本 / 持仓影响；preview 不产生任何业务写入。
- 加密 Draft Outbox，只重试“创建 DRAFT”。
- sourceRef 应用层幂等 + 数据库强幂等与并发冲突恢复。

## 记账安全边界

`文本/OCR/通知候选 → 用户复核 → DRAFT → preview → 用户二次确认 → 正式账本`

禁止自动 preview、自动 confirm、自动正式入账、自动交易，以及绕过后端账本校验。

转账（TRANSFER）同样走这条链路：解析只产生候选与 DRAFT，必须由主人补齐转出 / 转入账户、看到双账户预览并再次确认后，才通过 `QuickEntryService.quickTransfer` 写入一笔平衡转账流水。
投资（BUY / SUBSCRIPTION）同样走这条链路：解析只产生候选与 DRAFT，产品 ID 必须由主人明确选择（不按名称自动匹配），必须补齐真实产品与单一资金来源账户、看到资金影响预览并再次确认后，才通过 `OrderService.createInvestmentDraftOrder` 创建 PENDING 订单并生成下单付款账本；确认后仍不自动结算、不生成最终持仓。
卖出 / 赎回（SELL / REDEMPTION）同样走这条链路：解析只提取份额与产品名称提示，真实产品、持仓来源与到账账户必须由主人明确选择（不按名称自动匹配），看到可用份额与剩余份额预览并再次确认后，才通过 `OrderService.createSellRedeemDraftOrder` 创建 PENDING 订单并登记 SOURCE / TARGET 资金线；确认阶段不生成账本流水、不改现金余额、不改持仓，真正的资金与持仓变化仍只在后续人工结算时产生。
人工结算（settlement）同样走这条链路：PENDING 订单必须先调用只读 `POST /api/v2/settlements/preview`，主人看懂「哪些账户 +/− 多少现金、哪些持仓 +/− 多少份额、手续费多少」并二次确认后，才携带 preview 返回的 fresh 令牌调用 `POST /api/v2/settlements/confirm` 生成内部 `settlement_confirm` 与账本 / 持仓影响；preview 不写任何业务数据，令牌在订单 / 资金 / 账户 / 输入任一变化后立即失效。BUY / SUBSCRIPTION 结算不重复扣下单现金，SELL / REDEMPTION 结算才产生现金与持仓变化；重复确认幂等，仍不连接任何真实交易渠道。

## Android CI

v0.7.0 已有真实成功制品：

- Run ID `36320197608`
- Artifact ID `10931548073`
- APK `MyDCA-Board-v0.7.0-6098a972.apk`
- SHA-256 `D18D0CC67F7428495E6A6F2B0ED50100D556301368D6853FD0489AD2325E3B2B`

v0.8.0 已有真实成功 CI 制品：
- Run ID `36329990920`
- Artifact ID `10934923148`
- Artifact `mydca-android-v0.8.0-e8af769bf5446daa15ccf849b15a5f17786c7fcc`
- APK `MyDCA-Board-v0.8.0-e8af769b.apk`
- CI APK SHA-256 `F3C03CF9685C376782C2DB0CB799836971A63B5B4763BC38A9F1B0A96E837E08`

v0.9.0 已有真实成功 CI 制品：
- Run ID `36367375440`
- Artifact ID `10946904834`
- Artifact `mydca-android-v0.9.0-7fe07527a8d793018d4d7f284a5643359873ddd0`
- APK `MyDCA-Board-v0.9.0-7fe07527.apk`
- CI APK SHA-256 `5E1059E630D2F66C76A93271C62285C36276A7FAAA65867A445709AAD2A0A13C`

v0.10.0 已有真实成功 CI 制品：
- Run ID `36369966197`
- Artifact ID `10949105553`
- Artifact `mydca-android-v0.10.0-e5c544db7d90f82858ddc03a1ca285ec7659e037`
- APK `MyDCA-Board-v0.10.0-e5c544db.apk`
- CI APK SHA-256 `FD39508EA408807DB5ECEEBAFD2B2F4630D766447398E29D1397D8721A5304F0`
v0.11.0 制品：待 owner push 后回填（普通自动任务只提交、不 push，本轮不声称 CI APK 已交付）。
v0.12.0 制品：待 owner push 后回填（普通自动任务只提交、不 push，本轮不声称 CI APK 已交付）。
v0.13.0 制品：待 owner push 后回填（普通自动任务只提交、不 push，本轮不声称 CI APK 已交付）。

## 构建与验证

任务完成后必须运行：
`powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1`

成功构建只写 stdout/log，**不播放提示音，不弹 Windows Toast/Popup**。

Android 常用验证：
- `android-app\gradlew.bat --no-daemon testDebugUnitTest`
- `android-app\gradlew.bat --no-daemon assembleDebug`
- `android-app\gradlew.bat --no-daemon lintDebug`

## 当前下一步

v0.18 本地工程回归与发布收口记录见专项报告。目标环境配置观察表及既有 migration、历史目录权限、真实登录后的 PC/Android 断网与无障碍体验，以及推送后 CI APK 证据仍需部署方/主人验收。生产数据库迁移、真实财务记录修改和交易不属于本任务授权。

完整当前状态与长期文档关系见 `docs/CURRENT_DEVELOPMENT_STATE.md` 和 `docs/DOCUMENT_INDEX.md`。
