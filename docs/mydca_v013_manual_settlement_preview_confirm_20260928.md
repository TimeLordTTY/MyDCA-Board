# MyDCA v0.13.0 人工结算预览与二次确认闭环

- **作者**：Codex（AiCore 普通工程任务执行）
- **任务**：`task-mydca-v013-manual-settlement-preview-confirm-20260928`
- **工程仓库**：`TimeLordTTY/MyDCA-Board@v2`
- **风险级别 / 授权**：L3，owner 直接批准（`approval_source: direct_owner_chat`）
- **完成时间**：2026-09-28 +08:00

> 本文记录本次任务的当时事实。当前状态与后续计划以 `docs/CURRENT_DEVELOPMENT_STATE.md` 为准。

## 1. 目标

在 v0.11 / v0.12 已完成四类投资草稿与 PENDING 订单闭环的基础上，补齐财富中枢内部的人工结算能力：

`PENDING 订单 → 人工填写真实结算结果 → 只读 fresh settlement preview → 主人二次确认 → 财富中枢内部结算落账`

覆盖四类：`BUY`（买入）/ `SUBSCRIPTION`（申购）/ `SELL`（卖出）/ `REDEMPTION`（赎回）。

安全链路继续为：

`输入/解析 → DRAFT → fresh preview → 主人二次确认 → 内部结算落账`

本任务**只实现财富中枢内部的人工结算能力**：不连接券商 / 基金公司 / 交易所或任何真实交易接口，不自动结算，不后台 confirm，不调用外部交易渠道，不新增数据库表 / migration。

## 2. 交付内容

### 2.1 统一四类投资订单的 settlement 真实语义

不再另造第二套结算引擎，而是把既有 `SettlementService` 的「结算计算 / 校验」抽成无副作用的 preview 计算；preview 与 confirm 复用同一套纯函数规则（`runSettlement`，`apply=false` 只读 / `apply=true` 落账）：

- `BUY` / `SUBSCRIPTION`：下单阶段已生成付款账本（`CASH CREDIT` + `RECEIVABLE DEBIT`）；结算阶段**不重复扣下单现金**，效果为清理待结算应收（`RECEIVABLE`）、形成最终 `POSITION` / 关联账户影响并计提手续费（`FEE`）。
- `SELL` / `REDEMPTION`：下单阶段不生成账本，只有 `SOURCE` / `TARGET` 资金线；结算阶段才真正产生现金（`CASH`）、持仓（`POSITION`）与手续费（`FEE`）影响。

本轮用测试固定真实行为，并在本文档显式记录；未重做会计模型。

### 2.2 安全预览 API（只读）

新增：

```text
POST /api/v2/settlements/preview
```

输入（`SettlementPreviewRequest`）：`orderId`、`confirmDate`、`navDate`、`confirmNav`、`confirmShares`（BUY / SUBSCRIPTION 必要或按金额与净值计算）、`confirmAmount`（SELL / REDEMPTION 必要）、`confirmFee`（可选；`null` 表示按既有 `BrokerFeeService` 估算，明确输入 `0` 则使用 `0`）。

preview 校验：

- 订单存在、`status=PENDING`、订单属于当前 user / family 可见范围；
- 产品存在且启用；
- 资金来源行（funding lines）完整，且每条资金账户都在同一 owner scope 内可见；
- `confirmDate` / `navDate` 非空、`confirmNav>0`、份额 / 金额 / 手续费非负、SELL / REDEMPTION 的 `confirmAmount>0`。

preview **绝不**写 `settlement_confirm` / `ledger_txn` / `ledger_posting`，也**不改** `reserved_amount` / `initial_shares` / `order.status`。

返回 `SettlementPreviewDTO`，包含：`orderId` / `orderType` / `orderTypeLabel` / `orderStatus`、`productId` / `productName` / `productCode` / `currency`、`confirmDate` / `navDate` / `confirmNav` / `confirmShares` / `confirmAmount` / `confirmFee`、`computedShares` / `computedAmount` / `totalFundingAmount`、`warnings`、`confirmSupported`、`blockingReasons`、`postingsPreview[]`（`accountId` / `accountName` / `accountType` / `postingType` / `amount` / `shares` / `currency` / 中文 `description`）、`summaryLines[]`、`willCreateSettlementConfirm`、`willCreateLedgerTxn`、`willChangeHolding`、`willChangeCash`、`freshPreviewToken` / `previewFingerprint`。

`willChangeHolding` / `willChangeCash` / `willCreateLedgerTxn` 按类型真实返回；存在阻断原因时统一回落为 `false`。全部阻断原因与摘要均为中文，主人在确认前能看懂「哪些账户 +/− 多少现金、哪些持仓 +/− 多少份额、手续费多少」。

### 2.3 fresh preview gate

- `freshPreviewToken`（同时作为 `previewFingerprint`）由 `buildFingerprint` 生成并取 SHA-256：覆盖固定版本前缀、`orderId`、订单（`userId` / `productId` / `orderType` / `status` / `amount` / `shares` / `updatedAt`）、owner 作用域、输入参数、资金来源行、关键账户快照（余额 / `reserved_amount` / `initial_shares` / 币种 / 固定金额）、手续费与计算结果。
- confirm 时用同一套规则重新计算并逐字比对；**订单状态 / 更新时间、资金来源行、输入参数或关键账户快照任一变化都会使旧令牌失效**并阻断确认。
- 令牌不落库、不新增数据库字段，本轮不做 migration。

### 2.4 人工确认 API

```text
POST /api/v2/settlements/confirm
```

复用同一 `SettlementPreviewRequest`（含 `freshPreviewToken`）。confirm 时：

1. 再次校验当前用户 / 家庭权限与全部 preview 规则；
2. 校验 `freshPreviewToken` 与重新计算的指纹一致；
3. 令牌校验通过后才创建 preview 阶段登记的虚拟 / 持仓账户（`createPendingAccounts`），因此令牌失效时零副作用；
4. 写入唯一一条 `settlement_confirm`；
5. 释放资金来源行的 `reserved_amount`（仅对金额 > 0 的出资行）；
6. 更新关联账户 `initial_shares`；
7. 整个订单只生成一套 `ledger_txn` / `ledger_posting`；
8. 订单状态置为 `CONFIRMED`。

confirm 全程 `@Transactional`：同一订单已有 `settlement_confirm` 时直接返回既有结果，**重复确认幂等**；中途异常整体回滚，不产生半套流水。

### 2.5 权限与一致性加固

- confirm 入口不再只凭 `orderId` 操作：必须通过当前 user / family 可见范围校验；
- 资金来源行的 SOURCE / TARGET 账户也必须属于同一 owner scope；
- 产品与账户币种 / 类型按既有业务规则再次校验；
- 订单为 `CANCELLED` / `FAILED` / `CONFIRMED` 时不可生成新的结算；
- `confirmDate` / `navDate` / `confirmNav` / `confirmAmount` / `confirmShares` 非法负数或非法组合被阻断。

### 2.6 Android v0.13.0

- `versionName = 0.13.0`（`versionCode = 14`）。
- 新增「待结算」最小完整体验：待结算订单列表 → 结算编辑 → 结算影响预览 → 二次确认弹窗 → confirm 成功刷新；优先从总览 / 今日待办进入，不重做导航。
- 新增 `SettlementRepository`、`SettlementEditState`、`SettlementUiState`（`PendingSettlementUiState` / `SettlementPreviewUiState` / `SettlementStateHolder`）与 `PendingSettlementScreen`。
- 按 `orderType` 动态字段：
  - `BUY` / `SUBSCRIPTION`：确认日期、净值日期、实际净值、实际份额（可自动计算并展示计算值）、手续费；
  - `SELL` / `REDEMPTION`：确认日期、净值日期、实际净值、实际份额、实际确认金额、手续费。
- 按钮流程：「生成结算预览」→ 展示详细影响 → 「确认结算」→ 再弹一次中文确认弹窗 → 调 confirm。
- 修改任一字段即清除旧 preview；未 preview 不能 confirm；页面打开不自动 preview，更不自动 confirm；成功后刷新订单 / 持仓 / 资产相关页面（`onSettlementConfirmed`）。
- 新增 API：`GET /api/v2/settlements/pending`、`POST /api/v2/settlements/preview`、`POST /api/v2/settlements/confirm`。

### 2.7 PC / shared

- `web/shared`：`types/order.ts` 新增 `SettlementPreview` / `SettlementPostingPreview` / `SettlementPreviewRequest` 类型，`ConfirmSettlementRequest extends SettlementPreviewRequest`（携带 `freshPreviewToken`）；`api/settlement.ts` 新增 `previewSettlement`；新增 `utils/settlementPreview.ts`。
- PC `SettlementConfirmModal.vue` / `Orders.vue` / `Dashboard.vue` 与 mobile `Settlements.vue` 切换到同一「preview → fresh fingerprint → confirm」安全链，保持原能力兼容，不做视觉大重构。

## 3. 安全边界

- 仅代码开发与本地 / CI 测试：**不连接生产数据库、不修改真实财务记录、不调用外部交易服务、不自动结算、不绕过主人二次确认**。
- preview 是只读，不产生任何业务写入；只有主人二次确认后才执行内部 settlement 落账。
- 不自动 preview、不自动 confirm、不后台 confirm；OCR / 通知 / Share / Widget 任何入口都不能触发结算。
- 未新增数据库表或 migration，未新增系统权限。

## 4. 测试覆盖

后端 `SettlementServicePreviewTest`（24 项）：

| 任务要求 | 证据 |
| --- | --- |
| order 不存在 → 阻断 | `missingOrderIsBlocked` |
| 非 PENDING → 阻断 | `nonPendingOrderIsBlocked` |
| 已结算订单 → 阻断 | `alreadySettledOrderIsBlocked` |
| 越权 order → 阻断 | `foreignOrderIsBlocked` |
| funding line 越权 → 阻断 | `foreignFundingLineIsBlocked` |
| 净值 <= 0 → 阻断 | `zeroNavIsBlocked` |
| 负手续费 → 阻断 | `negativeFeeIsBlocked` |
| SELL 缺确认金额 → 阻断 | `sellWithoutAmountIsBlocked` |
| 缺确认日期 → 阻断 | `missingConfirmDateIsBlocked` |
| preview 无任何数据库写入 | `previewPerformsNoWrites` |
| preview 不改 reserved_amount / initial_shares / order.status | `previewDoesNotMutateOrderReservedOrInitialShares` |
| BUY preview 不重复扣下单现金 | `buyPreviewDoesNotDeductOrderCashAgain` |
| BUY / SUBSCRIPTION 展示 RECEIVABLE → POSITION / 关联账户 + fee | `buyPreviewShowsReceivableToPositionAndFee`、`subscriptionPreviewShowsReceivableToLinkedAccountCashAndFee` |
| SELL / REDEMPTION 展示 CASH / POSITION / fee | `sellPreviewShowsCashPositionAndFee`、`redemptionPreviewShowsCashPositionAndFee` |
| 四类均可生成受支持 preview | `allFourOrderTypesProduceSupportedPreview` |
| 订单变化 / 资金行变化 / 输入变化 → token 失效 | `tokenInvalidatedWhenOrderChanges`、`tokenInvalidatedWhenFundingLineChanges`、`tokenInvalidatedWhenInputChanges` |
| 缺 token 或旧 token → 阻断 confirm | `confirmWithoutTokenIsBlocked` |
| confirm 成功只生成一套 settlement / ledger | `confirmWithFreshTokenWritesSingleLedgerSet` |
| 重复 confirm 幂等，不重复流水 | `duplicateConfirmIsIdempotent` |
| confirm 中途异常整体回滚 | `confirmPropagatesLedgerFailureWithoutPartialOrderCommit` |
| v0.10 TRANSFER 与 v0.11 / v0.12 draft 回归不退化 | 后端全量测试通过（含既有 Order / Draft / Settlement 测试类） |

Android（`gradlew.bat --no-daemon testDebugUnitTest`）：

`SettlementEditStateTest`（27 项）：

| 任务要求 | 证据 |
| --- | --- |
| 四类订单字段差异 | `fieldLabelsDifferBetweenBuyAndSellOrders`、`sellFormKeepsConfirmAmountAndSharesSeparateFromBuyForm` |
| 仅接受四类投资类型 | `orderTypeHelpersOnlyAcceptFourInvestmentTypes` |
| 未 preview 不能 confirm | `confirmIsBlockedWithoutAnyPreview` |
| 只有同一订单的 fresh preview 才可 confirm | `confirmIsAllowedOnlyWithFreshPreviewOfSameOrder` |
| 无令牌 / preview 不支持 / 非本订单 → 阻断 | `confirmIsBlockedWithoutFreshPreviewToken`、`confirmIsBlockedWhenPreviewIsNotSupported`、`confirmIsBlockedWhenPreviewBelongsToAnotherOrderOrType` |
| 修改字段后旧 preview 失效 | `editingAnyFieldInvalidatesTheOldPreview` |
| 手续费：空 = 估算，显式 0 = 用 0 | `explicitZeroFeeIsKeptWhileBlankFeeMeansEstimate` |
| 非法日期 / 净值 / 负数 / SELL 缺金额 → 中文阻断 | `illegalDatesAndNavAreBlockedWithChineseReasons`、`negativeSharesAndNegativeFeeAreBlocked`、`sellRequestRequiresPositiveConfirmAmount` |
| 中文预览文案说明现金 / 持仓 / 手续费 | `previewTextExplainsCashHoldingAndFeeInChinese`、`previewTextShowsBlockingReasonWhenPreviewIsRejected`、`previewTextNeverPretendsThereIsAPreview` |
| 二次确认文案只做内部落账 | `confirmDialogCopyStatesInternalLedgerOnly` |
| 待结算计数只计 PENDING | `pendingCountOnlyCountsPendingOrders` |

`SettlementRepositoryTest`（6 项）：`listPendingSettlementsParsesBackendOrders`、`previewSettlementSendsReadOnlyRequestWithoutToken`、`confirmSettlementCarriesFreshPreviewToken`、`previewFailureIsReportedAsFailureNotFakeSuccess`、`confirmFailureNeverReportsSuccess`、`previewOnlyCallsPreviewEndpoint`。

`SettlementStateHolderTest`（4 项）：`loadPendingParsesOrdersAndResolvesProductNames`、`loadPendingKeepsPreviousOrdersWhenRefreshFails`、`productListFailureDoesNotHidePendingOrders`、`loadPendingWithoutProductRepositoryStillReturnsOrders`。

v0.7~v0.12 采集 / 草稿 / Widget / Share / 投资流程回归：Android 全量 `testDebugUnitTest` 通过。

## 5. 验证与结果

| 验证 | 命令 | 结果 |
| --- | --- | --- |
| 后端测试 | `backend: mvn -B test` | 通过：169 项，0 失败 / 0 错误（由 145 项增至 169 项，新增 `SettlementServicePreviewTest` 24 项） |
| Android 单元测试 | `android-app: gradlew.bat --no-daemon testDebugUnitTest` | 通过：267 项，0 失败 / 0 错误（由 230 项增至 267 项） |
| Android 打包 | `android-app: gradlew.bat --no-daemon assembleDebug` | 通过 |
| Android 静态检查 | `android-app: gradlew.bat --no-daemon lintDebug` | 通过 |
| shared 构建 | `web: npm -w @wealth-hub/shared run build` | 通过 |
| PC / mobile 类型检查 | `web: npm -w wealth-hub-pc-app type-check` 等 | 本仓库 PC / mobile `type-check` 基线已存在既有错误；本轮改动经 stash 对比未新增类型错误（净减少 1 条） |
| 仓库编译钩子 | `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1` | 通过，成功静默、无 Beep / Toast / Popup |
| 补丁卫生 | `git diff --check` | 通过，无空白错误 |

- debug APK 本地字节不可复现，本地观察值不作为制品身份；APK 不提交到 Git。

## 6. 版本与制品

| 项 | 值 | 证据 |
| --- | --- | --- |
| `versionName` | `0.13.0`（由 `0.12.0` 升级） | `android-app/app/build.gradle.kts` |
| `versionCode` | `14`（由 `13` 递增） | 同上 |
| APK 文件命名 | `MyDCA-Board-v0.13.0-<short-sha>.apk` | `.github/workflows/android-test-apk.yml` |
| Artifact 名 | `mydca-android-v0.13.0-<sha>` | 同上 |

- 继续使用一次性 debug 签名（`assembleDebug`），不新增 release signing key，APK 不提交到 Git。
- CI source commit / Run ID / Artifact ID / APK 文件名 / CI APK SHA-256：**待 owner push 后回填**。普通自动任务只提交、不 push，故本轮不声称 CI APK 已交付。

## 7. 与后续工作的关系

- 真机人工验收项：待结算列表加载、四类订单字段差异、结算影响预览中文文案、二次确认弹窗、成功后订单 / 持仓 / 资产刷新，以及 v0.11 / v0.12 投资草稿、v0.10.0 转账、v0.9.0 桌面小组件与 v0.8 分享入口。
- CI APK 证据回填依赖 owner / AiCore 真实推送到 `v2`；普通自动任务只提交、不 push。
- 四类投资订单（买入 / 申购 / 卖出 / 赎回）现已支持「只读预览 → 主人二次确认 → 内部结算落账」；confirm 只产生财富中枢内部 `settlement_confirm` 与账本 / 持仓影响，**不具备任何外部交易能力**。
- v0.8 数据库强幂等 migration 仍未部署（与本轮无关，需单独授权并先跑只读数据预检）。
- **不存在任何真实交易自动化**：不自动下单、不自动结算、不后台 confirm、不调用任何真实交易渠道。