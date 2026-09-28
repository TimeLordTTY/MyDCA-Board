# MyDCA v0.12.0 投资卖出 / 赎回草稿闭环

- **作者**：Codex（AiCore 普通工程任务执行）
- **任务**：`task-mydca-v012-invest-sell-redeem-draft-loop-20260928`
- **工程仓库**：`TimeLordTTY/MyDCA-Board@v2`
- **风险级别 / 授权**：L3，owner 直接批准（`approval_source: direct_owner_chat`）
- **完成时间**：2026-09-28 +08:00

> 本文记录本次任务的当时事实。当前状态与后续计划以 `docs/CURRENT_DEVELOPMENT_STATE.md` 为准。

## 1. 目标

把草稿闭环从 `EXPENSE` / `INCOME` / `TRANSFER` / `BUY` / `SUBSCRIPTION` 扩展到投资 **`SELL`（场内卖出）** 与 **`REDEMPTION`（场外赎回）**：

`文本候选或草稿编辑进入 SELL / REDEMPTION → 主人明确选择真实产品 + 该产品真实持仓来源 + 份额 + 到账账户 → 只读可用份额预览 → 主人二次确认 → 仅创建系统内 PENDING 记录`

安全链路完全不变：

`输入/解析 → DRAFT → fresh preview → 主人二次确认 → 仅内部 PENDING 记录`

本轮 **绝对不自动结算、不生成最终持仓、不生成任何账本流水、不调用券商 / 基金公司 / 任何真实交易渠道**。既有 `OrderService` 的真实语义必须保持：SELL / REDEMPTION 下单阶段不生成账本流水；真正 CASH / POSITION / FEE 变化只在后续 `SettlementService` 人工结算时产生。

## 2. 交付内容

### 2.1 统一投资候选结构

写入标准 JSON 时只使用下列标准字段：

| 字段 | 含义 |
| --- | --- |
| `txnType` | `SELL` 或 `REDEMPTION` |
| `productId` | 真实 `product_master.id`，必须由主人在 App / PC 明确选择 |
| `productNameHint` | 仅人工提示，**禁止**用它自动匹配真实 `productId` |
| `shares` | 本次要卖出 / 赎回的份额，必须大于 0 |
| `sourceAccountId` | 该产品当前真实的持仓来源账户 ID，必须由主人明确选择 |
| `sourceAccountNameHint` | 仅提示，**禁止**用它自动匹配真实账户 |
| `targetAccountId` | 到账账户 ID，必须由主人明确选择 |
| `targetAccountNameHint` | 仅提示 |
| `note` | 备注 |
| `expectedNavDate` | 可选预期净值日期 |
| `expectedConfirmDate` | 可选预期确认日期 |
| `orderType` | 与 `txnType` 一致，供订单侧复用既有语义 |

同步该约定的类型：后端 `AccountingIntentDTO`、`DraftPreviewDTO`；Android `AccountingIntentDto`、`DraftPreviewDto`；`web/shared` 的 `types/draft.ts`、`types/aiAccounting.ts`。

预览侧新增字段：`shares`、`availableShares`、`remainingShares`、`sharesMessage`。

### 2.2 文本候选识别 SELL / REDEMPTION（不自动匹配真实产品 / 来源 / 到账账户）

- `AiAccountingService.detectTxnType` 顺序为 **TRANSFER → 投资（含卖出 / 赎回）→ EXPENSE → INCOME**：先 `detectTransfer`，再 `detectInvestment`，投资判定优先于 `买` / `付款` / `支付` 等 EXPENSE 关键词。
- 命中 `卖出` → `SELL`；命中 `赎回` → `REDEMPTION`。
- 卖出 / 赎回文本只提取 `shares` 与 `productNameHint`：
  - `卖出半导体ETF 500份` → `SELL` + `shares=500` + `productNameHint=半导体ETF`；
  - `赎回兴全合润 1000份` → `REDEMPTION` + `shares=1000` + `productNameHint=兴全合润`；
  - 缺少份额时 `shares` 记入 `missingFields`。
- **禁止按产品名称自动匹配 `productId`，也禁止自动匹配持仓来源与到账账户**；缺字段时只生成 DRAFT，并把对应字段写入 `missingFields`。

### 2.3 后端卖出 / 赎回只读预览

`DraftLedgerEntryService.buildPreview` 对 SELL / REDEMPTION 委派到 `buildSellRedeemPreview`，只读读取产品、真实持仓来源与到账账户，并重新校验：

- 产品：`productId` 存在、产品启用（`is_active`）、产品币种与到账账户币种一致。
- 持仓来源：`shares > 0`；`sourceAccountId` 必须命中 `HoldingService.getProductHoldingsByAccount(productId, userId, familyId)` 返回的真实持仓来源；可用份额 = 该来源账户真实持仓份额 − 同产品 / 来源账户下仍为 PENDING 的 SELL / REDEMPTION 占用份额（`OrderFundingLineMapper.sumPendingSellSharesByAccount`），避免内部重复占用；`shares` 不得超过可用份额。
- 到账账户：当前 user / family 可见（`is_active=1` 且 `account_kind='REAL'`）、叶子账户、币种与产品一致；禁止 `VIRTUAL` / `POSITION` / 父账户。

预览影响口径严格为「只登记内部占用」：

| 预览字段 | SELL / REDEMPTION 取值 |
| --- | --- |
| `willCreateOrder` | 字段齐全时 `true` |
| `willCreateLedgerTxn` | `false` |
| `willCreateSettlement` | `false` |
| `willAffectHolding` | `false` |
| `impactDirection` | `NONE` |
| `accountDelta` / `targetAccountDelta` / `receivableDelta` | `0` |
| `shares` / `availableShares` / `remainingShares` | 本次份额 / 当前可用份额 / 预计剩余份额 |
| `sharesMessage` | 「确认后只创建内部 PENDING … 记录并占用 … 份；不会立即减少持仓，也不会立即增加 … 的到账余额。」 |

**preview 绝不调用 `OrderService.createOrder` / `createSellRedeemDraftOrder`、`LedgerService` 或 `SettlementService`。**

### 2.4 后端正式登记

新增 `OrderService.createSellRedeemDraftOrder(...)` 安全入口，只允许 SELL / REDEMPTION，并重新校验产品、持仓来源、可用份额与到账账户可见性：

- 复用既有 `OrderService` 创建 `status=PENDING` 订单；
- 写入 `SOURCE` 资金线（`sourceAccountId` + `shares` + `lineType=SOURCE`）；
- 写入 `TARGET` 资金线（`targetAccountId` + `lineType=TARGET`）；
- **confirm 阶段不生成 CASH / POSITION / FEE 账本流水、不改现金余额、不改持仓、不调用 `SettlementService`**；
- 本轮未新增数据库表 / migration。

### 2.5 confirm

- 第一次 confirm 前必须重新 `buildPreview`；只有 `confirmSupported=true` 才调用安全入口。
- 订单 ID 写回 `confirmOrderId`；已 CONFIRMED 的草稿再次 confirm 直接返回，**重复确认幂等**。
- `markConfirmed` 影响行数不为 1 时抛出异常，事务整体回滚，草稿保持 `DRAFT`。
- `IGNORED` 草稿不可确认；不存在自动 preview / auto confirm / auto settle。

### 2.6 Android v0.12.0

- `versionName = 0.12.0`（`versionCode = 13`）。
- 草稿编辑新增「卖出 / 赎回」类型；表单为真实产品、持仓来源、份额、到账账户、备注。
- 产品选定后复用 `GET /api/v2/holdings/product/{productId}/by-account` 只读展示持仓来源（`MobileHoldingByAccountDto` + `WealthHubApi.getProductHoldingsByAccount`），不按名称猜测来源。
- 到账账户只允许当前可见、active REAL 叶子账户，禁止 VIRTUAL / POSITION / 父账户，且币种必须与所选产品一致。
- 确认文案明确：当前只创建内部待处理记录，不立即减少持仓，也不立即增加到账余额。
- 切换到其它交易类型时清理 SELL / REDEMPTION 专属字段（份额 / 持仓来源 / 到账账户），保证候选 payload 干净。

### 2.7 PC / shared

- `web/shared` 同步 SELL / REDEMPTION 类型与预览字段（`shares` / `availableShares` / `remainingShares` / `sharesMessage`、`sourceAccountId` / `sourceAccountNameHint`），并更新 `willCreateLedgerTxn` / `willCreateOrder` / `willCreateSettlement` / `willAffectHolding` 的语义注释。
- PC 草稿箱支持 SELL / REDEMPTION 查看、编辑、preview、二次确认：编辑态可选真实产品 + 该产品真实持仓来源（`holdingApi.getProductHoldingsByAccount`）+ 份额 + 到账账户；预览展示产品、持仓来源、可用 / 本次 / 预计剩余份额、到账账户与份额占用说明；确认弹窗明确只创建内部 PENDING 记录。

## 3. 安全边界

- 仅代码开发与本地 / CI 测试：**不连接生产数据库、不修改真实财务记录、不调用外部交易服务、不自动结算、不绕过主人二次确认**。
- SELL / REDEMPTION confirm 只创建系统内 PENDING 记录并登记份额占用，不产生任何账本流水、不改现金余额、不改持仓。
- 文本解析只输出候选与 DRAFT，绝不自动匹配真实产品 / 持仓来源 / 到账账户。
- 未新增数据库表或 migration，未新增系统权限。

## 4. 测试覆盖

后端 `DraftLedgerEntryServiceSellRedeemTest`（20 项）：

| 任务要求 | 证据 |
| --- | --- |
| 缺份额 | `sellPreviewMissingSharesCannotConfirm` |
| 缺持仓来源 | `redemptionPreviewMissingSourceAccountCannotConfirm` |
| 缺到账账户 | `sellPreviewMissingTargetAccountCannotConfirm` |
| 缺产品 | `sellPreviewMissingProductCannotConfirm` |
| 份额 <= 0 | `nonPositiveSharesBlocksConfirm` |
| 产品停用 | `inactiveProductBlocksConfirm` |
| 无真实持仓 | `accountWithoutRealHoldingBlocksConfirm` |
| 份额超限 | `sharesOverAvailableBlocksConfirm` |
| 已有 PENDING 后再次超限 | `pendingOccupiedSharesReduceAvailability` |
| PENDING 已占满可用份额 | `fullyPendingOccupiedSharesBlockAnySell` |
| 非法 target（POSITION / 父账户 / 不可见 / 币种不一致） | `positionTargetAccountBlocksConfirm`、`parentTargetAccountBlocksConfirm`、`invisibleTargetAccountBlocksConfirm`、`currencyMismatchTargetBlocksConfirm` |
| preview 不写业务 | `sellPreviewIsReadOnlyAndOnlyWritesDraftPreviewPayload` |
| confirm 创建单一 PENDING + SOURCE / TARGET、现金与持仓不变 | `confirmSellCreatesSinglePendingOrderWithoutTouchingCashOrHolding`、`confirmRedemptionCreatesSinglePendingOrder` |
| fresh preview gate 不退化 | `confirmRejectsFreshPreviewWhenSharesExceedAvailability` |
| 重复确认幂等 | `confirmingAlreadyConfirmedSellDraftIsIdempotent` |
| 异常回滚 | `failingMarkConfirmedRollsBackSellDraftToDraft` |

文本解析（`AiAccountingServiceInvestmentTest`）：

| 任务要求 | 证据 |
| --- | --- |
| 「卖出半导体ETF 500份」→ SELL + shares + productNameHint | `parseSellTextProducesSellCandidateWithSharesAndProductHint` |
| 「赎回兴全合润 1000份」→ REDEMPTION | `parseRedeemTextProducesRedemptionCandidateWithSharesAndProductHint` |
| 缺份额写入 missingFields | `sellTextWithoutSharesReportsMissingShares` |
| 候选 JSON 不含自动匹配的 productId / 账户 | `sellIntentJsonNeverContainsAutoMatchedProductOrAccounts` |
| BUY / SUBSCRIPTION / TRANSFER / EXPENSE / INCOME 全回归 | `AiAccountingServiceTest`、`DraftLedgerEntryServiceTest`、`DraftLedgerEntryServiceInvestmentTest`、`DraftLedgerEntryConcurrentReplayTest` 全量通过 |

Android（`gradlew.bat --no-daemon testDebugUnitTest`）：

| 任务要求 | 证据 |
| --- | --- |
| 卖出 / 赎回为受支持类型 | `DraftEditStateTest#sellAndRedemptionAreSupportedTypes` |
| 表单校验份额 / 来源 / 到账账户 | `DraftEditStateTest#sellFormRequiresSharesSourceAndTarget` |
| payload 只带 shares / source、不带 amount | `DraftEditStateTest#sellPayloadCarriesSharesSourceAndDropsAmount` |
| 切回普通记账类型清空卖出字段 | `DraftEditStateTest#switchingFromSellToExpenseClearsSellFields` |
| 从草稿回读卖出 / 赎回字段 | `DraftEditStateTest#formFromDraftReadsBackSellRedeemFields` |
| 到账账户只允许 REAL 叶子、非 POSITION、币种一致 | `DraftAccountSelectionTest#sellRedeemTargetOnlyAllowsRealLeafNonPositionMatchingCurrency` |
| preview 展示份额 / 来源 / 到账账户 | `DraftReviewTest#sellRedeemPreviewShowsSharesSourceAndTarget` |
| 影响摘要读取份额 | `DraftReviewTest#sellRedeemImpactSummaryReadsShares` |
| 二次确认文案只创建内部待处理记录 | `DraftReviewTest#sellRedeemConfirmDialogStatesPendingOnlyNoImmediateImpact` |

## 5. 验证与结果

| 验证 | 命令 | 结果 |
| --- | --- | --- |
| 后端测试 | `backend: mvn -B test` | 通过：16 个测试类共 145 项，0 失败 / 0 错误 / 0 跳过（由 121 项增至 145 项） |
| Android 单元测试 | `android-app: gradlew.bat --no-daemon testDebugUnitTest` | 通过：35 个测试类共 230 项，0 失败 / 0 错误 / 0 跳过（由 221 项增至 230 项） |
| Android 打包 | `android-app: gradlew.bat --no-daemon assembleDebug` | 通过 |
| Android 静态检查 | `android-app: gradlew.bat --no-daemon lintDebug` | 通过：0 error，2 条既有 warning（`DataExtractionRules`、`ObsoleteSdkInt`，非本轮引入） |
| PC / shared 构建 | `web: npm run build:shared`、`npm run build:pc`、`npm run build:mobile`、`npm run -w wealth-hub-pc-app type-check` | 通过（`DraftInbox.vue` 无类型错误；`Dashboard.vue` / `Orders.vue` / `Products.vue` 等既有错误与本轮无关） |
| 仓库编译钩子 | `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1` | 通过（后端 `mvn -DskipTests package` + 前端 `npm run build`），成功静默、无 Beep / Toast / Popup |
| 补丁卫生 | `git diff --check` | 通过，无空白错误 |

- debug APK 本地字节不可复现（同一份源码连续两次 `assembleDebug` 的体积与 SHA-256 都会变化），本地观察值不作为制品身份；APK 不提交到 Git。

## 6. 版本与制品

| 项 | 值 | 证据 |
| --- | --- | --- |
| `versionName` | `0.12.0`（由 `0.11.0` 升级） | `android-app/app/build.gradle.kts` |
| `versionCode` | `13`（由 `12` 递增） | 同上 |
| APK 文件命名 | `MyDCA-Board-v0.12.0-<short-sha>.apk` | `.github/workflows/android-test-apk.yml` |
| Artifact 名 | `mydca-android-v0.12.0-<sha>` | 同上 |

- 继续使用一次性 debug 签名（`assembleDebug`），不新增 release signing key，APK 不提交到 Git。
- CI source commit / Run ID / Artifact ID / APK 文件名 / CI APK SHA-256：**待 owner push 后回填**。普通自动任务只提交、不 push，故本轮不声称 CI APK 已交付。

## 7. 与后续工作的关系

- 真机人工验收项：卖出 / 赎回草稿的持仓来源选择、可用份额与剩余份额预览、到账账户过滤与二次确认文案，以及 v0.11.0 投资买入 / 申购、v0.10.0 转账、v0.9.0 桌面小组件与 v0.8 分享入口。
- CI APK 证据回填依赖 owner / AiCore 真实推送到 `v2`；普通自动任务只提交、不 push。
- 四类投资动作（买入 / 申购 / 卖出 / 赎回）均已纳入草稿安全链路。**确认阶段仍不生成最终持仓**：SELL / REDEMPTION 只登记内部 PENDING 占用；真正的 CASH / POSITION / FEE 变化只在后续 `SettlementService` 人工结算时产生。
- 下一步的完整结算 / 持仓影响闭环尚无 owner 授权任务，需单独规划与批准后才能推进。
- v0.8 数据库强幂等 migration 仍未部署（与本轮无关，需单独授权并先跑只读数据预检）。
- **不存在任何真实交易自动化**：不自动下单、不自动结算、不生成最终持仓、不调用任何真实交易渠道。