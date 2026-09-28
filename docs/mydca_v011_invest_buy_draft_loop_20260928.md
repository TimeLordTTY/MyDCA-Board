# MyDCA v0.11.0 投资买入 / 申购草稿闭环

- **作者**：Codex（AiCore 普通工程任务执行）
- **任务**：`task-mydca-v011-invest-buy-draft-loop-20260928`
- **工程仓库**：`TimeLordTTY/MyDCA-Board@v2`
- **风险级别 / 授权**：L3，owner 直接批准（`approval_source: direct_owner_chat`）
- **完成时间**：2026-09-28 +08:00

> 本文记录本次任务的当时事实。当前状态与后续计划以 `docs/CURRENT_DEVELOPMENT_STATE.md` 为准。

## 1. 目标

把草稿闭环从 `EXPENSE` / `INCOME` / `TRANSFER` 扩展到投资 **`BUY`（场内买入）** 与 **`SUBSCRIPTION`（场外申购）**：

`文本候选或草稿编辑进入 BUY / SUBSCRIPTION → 主人明确选择真实产品 + 单一资金来源账户 + 金额 → 只读资金影响预览 → 主人二次确认 → 复用现有 OrderService 创建系统内 PENDING 订单`

安全链路完全不变：

`输入/解析 → DRAFT → fresh preview → 主人二次确认 → 正式订单 / 账本`

本轮 **绝对不自动结算、不自动生成最终持仓、不调用券商 / 基金公司 / 任何真实交易渠道**。

## 2. 交付内容

### 2.1 统一投资候选结构

写入标准 JSON 时只使用下列标准字段：

| 字段 | 含义 |
| --- | --- |
| `txnType` | `BUY` 或 `SUBSCRIPTION` |
| `productId` | 真实 `product_master.id`，必须由主人在 App / PC 明确选择 |
| `productNameHint` | 仅人工提示，**禁止**用它自动匹配真实 `productId` |
| `amount` | 正数金额 |
| `accountId` | 本轮单资金来源真实叶子账户 ID |
| `accountNameHint` | 仅提示 |
| `note` | 备注 |
| `expectedNavDate` | 可选预期净值日期 |
| `expectedConfirmDate` | 可选预期确认日期 |

本轮 **只支持单资金来源账户**；`OrderService` 既有组合支付能力保持不变，组合资金来源留给后续独立任务。

同步该约定的类型：后端 `AccountingIntentDTO`、`DraftPreviewDTO`；Android `AccountingIntentDto`、`DraftPreviewDto`；`web/shared` 的 `types/draft.ts`、`types/aiAccounting.ts`。

### 2.2 文本候选识别 BUY / SUBSCRIPTION（不自动匹配真实产品）

- `AiAccountingService.detectTxnType` 顺序为 **TRANSFER → 投资 → EXPENSE → INCOME**：先 `detectTransfer`，再 `detectInvestment`，投资判定优先于 `买` / `付款` / `支付` 等 EXPENSE 关键词。
- `detectInvestment`：命中 `买入` → `BUY`；命中 `申购` 或 `定投` → `SUBSCRIPTION`。
- `买入 XXX 1000` → `BUY`；`申购 XXX 1000` → `SUBSCRIPTION`；`定投 XXX 1000` → `SUBSCRIPTION`。
- `买奶茶 30` 仍为 `EXPENSE`（不含 `买入`）；`支付午饭 30` 仍为 `EXPENSE`。
- 保守提取：金额、`productNameHint`。金额提取先剔除产品名称提示，避免「买入沪深300ETF 1000」把产品名里的 `300` 误当金额，最终取剩余文本最后一个安全金额候选。
- **禁止按产品名称自动匹配 productId**；缺失时只生成 DRAFT，并把 `productId` / `accountId` 写入 `missingFields`。

### 2.3 后端投资只读预览

`DraftLedgerEntryService.buildPreview` 对 BUY / SUBSCRIPTION 委派到 `buildInvestmentPreview`，读取产品与单一资金来源账户，并重新校验：

产品：`productId` 存在、产品启用（`is_active`）、产品币种与账户币种一致、BUY / SUBSCRIPTION 与产品类型不明显冲突。
资金账户：当前 user / family 可见（`selectVisibleRealById`：`is_active=1` 且 `account_kind='REAL'` 且归属作用域匹配）、叶子账户（`selectChildren` 为空）、`balance - reserved_amount >= amount`。

资金用途规则严格按现有 `Account` 设计：

- 一般投资：只允许 `INVESTABLE`；
- `BOND_REPO`：允许 `INVESTABLE` 或 `RESERVED`；
- `SPENDABLE`：不直接用于普通投资，中文提示「请先通过 TRANSFER 调整到 INVESTABLE 叶子账户」；
- `RESERVED`：除国债逆回购外不允许普通投资；
- 父账户 / VIRTUAL / inactive 一律阻断。

预览新增字段：`orderType`、`productId`、`productName`、`productCode`、`productAssetType`、`productCurrency`、`availableBefore`、`receivableDelta`、`expectedNavDate`、`expectedConfirmDate`、`fundingMessage`。
投资影响口径：`accountDelta = -amount`（`impactDirection = DECREASE`），`receivableDelta = +amount`，`willCreateOrder = true`，`willCreateLedgerTxn = true`，`willCreateSettlement = false`，`willAffectHolding = false`。

中文说明明确为：「确认后将创建 PENDING 买入/申购订单，并立即生成下单付款账本；资金账户减少 ¥X，待结算应收增加 ¥X。此时尚未结算，也不会生成最终持仓。」

预览阶段只读：**不调用** `OrderService.createOrder`、`LedgerService.createTransaction` 或 `SettlementService`。

### 2.4 OrderService 安全入口

新增 `OrderService.createInvestmentDraftOrder(userId, familyId, productId, orderType, amount, accountId, expectedNavDate, expectedConfirmDate, note)`：

- 只允许 `BUY` / `SUBSCRIPTION`；`productId` / `accountId` 必须非空；`amount > 0`；
- 重新校验产品存在且启用、账户当前 user / family 可见且为 REAL 叶子账户、币种一致、资金用途合规（一般投资只允许 `INVESTABLE`，`BOND_REPO` 额外允许 `RESERVED`）、可用余额 `balance - reserved_amount >= amount`；
- 校验通过后复用既有 `createOrder`，创建 `orders.status=PENDING` 订单并生成下单付款账本（CASH CREDIT + RECEIVABLE DEBIT）；
- 不自动结算、不生成最终持仓、不调用任何真实交易渠道。

同时修正 `OrderService` 注释，使其与当前代码事实一致（不再写「下单只增加 `reserved_amount`」），不改变订单现有会计语义。

### 2.5 草稿 confirm 扩展

- `confirmDraft` 对 BUY / SUBSCRIPTION **重新 `buildPreview`**，不信任草稿里旧 preview JSON；只有 `preview.confirmSupported = true` 才调用 `createInvestmentDraftOrder`。
- 确认后把订单 ID 写回既有 `confirmOrderId`（`markConfirmed(draftId, txnId, orderId)`），草稿进入 `CONFIRMED`。
- **不调用 SettlementService**。
- 重复确认：已 `CONFIRMED` 直接返回，绝不创建第二张订单或第二笔付款账本。
- 任何异常整体事务回滚，草稿保持 `DRAFT`。

### 2.6 Android v0.11.0

- `data/dto/DraftDtos.kt` / `AiAccountingDtos.kt`：新增投资预览与候选字段，新增 `ProductDto`。
- `data/api/WealthHubApi.kt` + `data/repository/WealthRepository.kt`：新增 `GET api/v2/products`（`getProducts(keyword, assetType)`）。
- `ui/state/DraftEditState.kt`：草稿箱支持 `BUY` / `SUBSCRIPTION` 类型；表单新增 `productId` / `productNameHint` / `expectedNavDate` / `expectedConfirmDate`；切回普通记账类型时清空投资字段；payload 归一化写回标准字段。
- `ui/state/DraftAccountSelection.kt`：新增 `isEligibleInvestmentAccount()`（`INVESTABLE` 叶子账户；`BOND_REPO` 额外允许 `RESERVED`）与投资拒绝原因。
- `ui/state/DraftReview.kt`：投资影响行、投资确认弹窗标题「确认创建【产品】买入/申购订单 ¥X？」与正文「确认后将立即从【账户】扣除 ¥X，并增加同额待结算应收；订单仍需后续结算，不会自动成交。」
- `ui/screens/DraftInboxScreen.kt`：买入 / 申购类型按钮、产品选择、投资资金账户过滤、产品与账户预览行、确认弹窗。
- `app/build.gradle.kts`：`versionCode 11 → 12`、`versionName 0.10.0 → 0.11.0`。

Android 的过滤 / 提示只是体验层，最终安全边界以后端为准。

### 2.7 PC / shared

- `web/shared/src/types/draft.ts`、`aiAccounting.ts`：新增投资预览 / 候选字段与 BUY / SUBSCRIPTION 说明。
- `web/pc-app/src/views/DraftInbox.vue`：买入 / 申购类型、产品选择 + 名称提示 + 净值 / 确认日期、投资资金账户选项、产品与付款账本影响预览、二次确认弹窗；不重构现有 `NewOrderModal`，不自动 settle。

## 3. 安全边界

- 不自动 preview、不自动 confirm、不自动下单、不自动结算、不自动生成持仓、不调用真实交易渠道。
- 不把 `productNameHint` 自动解析为真实 `productId`。
- OCR / 通知 / 分享 / Widget 入口不获得任何投资下单能力。
- 不绕过当前 user / family 可见性、币种、资金用途与可用余额校验。
- 确认创建订单会生成付款账本（后续仍需 SettlementService 结算），但本轮不结算、不生成最终持仓。
- 不新增数据库表、不新增 migration。
- SELL / REDEMPTION 本轮不做，作为下一阶段独立任务。

## 4. 变更清单

后端（`backend/**`）：

- `dto/AccountingIntentDTO.java`：新增 `productId`、`productNameHint`、`expectedNavDate`、`expectedConfirmDate`。
- `dto/DraftPreviewDTO.java`：新增 `orderType`、`productId`、`productName`、`productCode`、`productAssetType`、`productCurrency`、`availableBefore`、`receivableDelta`、`expectedNavDate`、`expectedConfirmDate`、`fundingMessage`。
- `service/AiAccountingService.java`：`detectInvestment`（TRANSFER 之后、EXPENSE 之前）；`extractProductNameHint`、`extractInvestmentAmount`；`missingFields` 增加 `productId`；`toIntentJson` / `normalizeIntent` 写回投资字段。
- `service/DraftLedgerEntryService.java`：`buildInvestmentPreview`（只读，与 confirm 复用同一套规则）；`confirmDraft` 走 `createInvestmentDraftOrder`，写回 `confirmOrderId`。
- `service/OrderService.java`：新增 `createInvestmentDraftOrder` 安全入口；修正过期注释。
- `controller/DraftLedgerEntryController.java`：javadoc 明确 BUY / SUBSCRIPTION。
- 测试：`DraftLedgerEntryServiceTest`、`DraftLedgerEntryConcurrentReplayTest` 扩展 / 修正；新增 `DraftLedgerEntryServiceInvestmentTest`（20 项）、`AiAccountingServiceInvestmentTest`（6 项）。

Android（`android-app/**`）：

- `data/dto/DraftDtos.kt`、`data/dto/AiAccountingDtos.kt`、`data/api/WealthHubApi.kt`、`data/repository/WealthRepository.kt`：投资 DTO 与产品接口。
- `ui/state/DraftEditState.kt`、`DraftAccountSelection.kt`、`DraftReview.kt`、`ui/screens/DraftInboxScreen.kt`：投资类型 / 产品 / 资金账户 / 预览 / 确认文案。
- `app/build.gradle.kts`：`versionCode 12`、`versionName 0.11.0`。
- 测试：`DraftEditStateTest`、`DraftAccountSelectionTest`、`DraftReviewTest` 扩展；`WealthRepositoryTest`、`WealthStateHolderTest`、`TodayTodoStateHolderTest` 适配产品接口。

Web（`web/**`）：

- `shared/src/types/draft.ts`、`shared/src/types/aiAccounting.ts`、`pc-app/src/views/DraftInbox.vue`：投资类型、产品选择、资金账户、预览与二次确认。

工作流与文档：`.github/workflows/android-test-apk.yml`（v0.11.0 命名）、`README.md`、`android-app/README.md`、`docs/CURRENT_DEVELOPMENT_STATE.md`、`docs/Phase3-开发进度总结.md`、`docs/Phase3-移动端原生App与自动记账.md`、`docs/DOCUMENT_INDEX.md`、本文件。

## 5. 测试矩阵（任务要求 → 证据）

后端（`mvn -B test`）：

| 任务要求 | 证据 |
| --- | --- |
| 1. BUY 缺 productId → 不可确认 | `DraftLedgerEntryServiceInvestmentTest#buyPreviewWithoutProductIdCannotConfirm` |
| 2. SUBSCRIPTION 缺 productId → 不可确认 | `DraftLedgerEntryServiceInvestmentTest#subscriptionPreviewWithoutProductIdCannotConfirm` |
| 3. 缺 accountId → 不可确认 | `DraftLedgerEntryServiceInvestmentTest#missingAccountIdBlocksConfirm` |
| 4. amount <= 0 → 阻断 | `DraftLedgerEntryServiceInvestmentTest#nonPositiveAmountBlocksConfirm` |
| 5. 产品不存在 / inactive → 阻断 | `DraftLedgerEntryServiceInvestmentTest#missingProductBlocksConfirm`、`#inactiveProductBlocksConfirm` |
| 6. 账户不可见 → 阻断 | `DraftLedgerEntryServiceInvestmentTest#invisibleOrVirtualAccountBlocksConfirm` |
| 7. 父账户 / VIRTUAL → 阻断 | `DraftLedgerEntryServiceInvestmentTest#parentAccountBlocksConfirm`、`#invisibleOrVirtualAccountBlocksConfirm` |
| 8. 币种不一致 → 阻断 | `DraftLedgerEntryServiceInvestmentTest#currencyMismatchBlocksConfirm` |
| 9. 普通产品使用 SPENDABLE → 阻断 | `DraftLedgerEntryServiceInvestmentTest#spendableAccountBlocksNormalInvestment` |
| 10. 普通产品使用 RESERVED → 阻断 | `DraftLedgerEntryServiceInvestmentTest#reservedAccountBlocksNormalInvestment` |
| 11. INVESTABLE → 允许普通投资 | `DraftLedgerEntryServiceInvestmentTest#investableAccountAllowsNormalInvestmentWithLedgerImpact` |
| 12. BOND_REPO + RESERVED → 允许 | `DraftLedgerEntryServiceInvestmentTest#bondRepoWithReservedAccountIsAllowed` |
| 13. 可用余额不足 → 阻断 | `DraftLedgerEntryServiceInvestmentTest#insufficientAvailableBalanceBlocksConfirm` |
| 14. preview 不创建 order / txn / settlement | `DraftLedgerEntryServiceInvestmentTest#previewNeverCreatesOrderLedgerOrSettlement` |
| 15. preview 显示 CASH -amount / RECEIVABLE +amount | `DraftLedgerEntryServiceInvestmentTest#investableAccountAllowsNormalInvestmentWithLedgerImpact` |
| 16. confirm BUY 创建一张 PENDING order 并生成付款账本 | `DraftLedgerEntryServiceInvestmentTest#confirmBuyDraftCreatesSinglePendingOrderAndMarksDraftConfirmed` |
| 17. confirm SUBSCRIPTION 同样正确 | `DraftLedgerEntryServiceInvestmentTest#confirmSubscriptionDraftCreatesOrderWithoutSettlement` |
| 18. confirm 不调用 SettlementService | `DraftLedgerEntryServiceInvestmentTest#confirmSubscriptionDraftCreatesOrderWithoutSettlement` |
| 19. 第二次 confirm 不重复 order / ledger | `DraftLedgerEntryServiceInvestmentTest#confirmingAlreadyConfirmedDraftDoesNotCreateSecondOrder` |
| 20. 中途异常事务回滚，草稿仍 DRAFT | `DraftLedgerEntryServiceInvestmentTest#failingMarkConfirmedRollsBackDraftToDraft` |
| 21. EXPENSE / INCOME / TRANSFER 全量回归 | `DraftLedgerEntryServiceTest`、`AiAccountingServiceTest`、`DraftLedgerEntryConcurrentReplayTest` 全量通过 |

文本解析（`AiAccountingServiceInvestmentTest`）：

| 任务要求 | 证据 |
| --- | --- |
| 「买入沪深300ETF 1000」→ BUY + amount + productNameHint，productId / accountId 缺失 | `parseBuyTextProducesBuyCandidateWithProductHintAndMissingIds` |
| 「申购兴全合润 500」→ SUBSCRIPTION | `parseSubscriptionTextProducesSubscriptionCandidate` |
| 「定投纳指 500」→ SUBSCRIPTION | `parseAutoInvestTextProducesSubscriptionCandidate` |
| 「买奶茶30」→ EXPENSE，不误判 BUY | `plainBuyingMilkTeaStaysExpense` |
| 「支付午饭 30」→ EXPENSE | `payingForLunchStaysExpense` |
| 候选 JSON 不含自动匹配的 productId | `investmentIntentJsonNeverContainsAutoMatchedProductId` |

Android（`gradlew.bat --no-daemon testDebugUnitTest`）：

| 任务要求 | 证据 |
| --- | --- |
| BUY / SUBSCRIPTION 编辑状态 | `DraftEditStateTest` 投资类型与字段读写用例 |
| 产品切换 | `DraftEditStateTest` / `DraftInboxScreen` 产品选择用例 |
| 资金账户过滤 | `DraftAccountSelectionTest` 投资账户过滤用例 |
| SPENDABLE / RESERVED 普通投资阻断 | `DraftAccountSelectionTest` 投资拒绝原因用例 |
| BOND_REPO + RESERVED 可选 | `DraftAccountSelectionTest` 逆回购账户用例 |
| 切回普通记账类型后投资字段清空 | `DraftEditStateTest` payload 归一化用例 |
| 保存后 preview 失效 | 既有草稿箱保存路径回归用例 |
| preview 显示付款账户减少 + 待结算应收增加 | `DraftReviewTest` 投资影响行用例 |
| 二次确认文案准确 | `DraftReviewTest` 投资确认弹窗标题 / 正文用例 |
| fresh preview gate 不退化 | 既有 confirm gating 用例回归 |
| v0.7 ~ v0.10 OCR / Outbox / Share / Widget / TRANSFER 回归 | 全量测试类通过 |

## 6. 验证与结果

| 验证 | 命令 | 结果 |
| --- | --- | --- |
| 后端测试 | `backend: mvn -B test` | 通过：15 个测试类共 121 项，0 失败 / 0 错误 / 0 跳过（由 95 项增至 121 项） |
| Android 单元测试 | `android-app: gradlew.bat --no-daemon testDebugUnitTest` | 通过：221 项，0 失败 / 0 错误 / 0 跳过（由 206 项增至 221 项） |
| Android 打包 | `android-app: gradlew.bat --no-daemon assembleDebug` | 通过 |
| Android 静态检查 | `android-app: gradlew.bat --no-daemon lintDebug` | 通过：0 error，2 条既有 warning（`DataExtractionRules`、`ObsoleteSdkInt`，非本轮引入） |
| PC / shared 构建 | `web: npm run build:shared`、`npm run build:pc`、`npm run -w wealth-hub-pc-app type-check` | 通过（`DraftInbox.vue` 无新增类型错误；其余 `Dashboard.vue` / `Orders.vue` / `Products.vue` 既有错误与本轮无关） |
| 仓库编译钩子 | `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1` | 通过（后端 `mvn -DskipTests package` + 前端 `npm run build`），成功静默、无 Beep / Toast / Popup |
| 补丁卫生 | `git diff --check` | 通过，无空白错误 |

- debug APK 本地字节不可复现（同一份源码连续两次 `assembleDebug` 的体积与 SHA-256 都会变化），本地观察值不作为制品身份；APK 不提交到 Git。

## 7. 版本与制品

| 项 | 值 | 证据 |
| --- | --- | --- |
| `versionName` | `0.11.0`（由 `0.10.0` 升级） | `android-app/app/build.gradle.kts` |
| `versionCode` | `12`（由 `11` 递增） | 同上 |
| APK 文件命名 | `MyDCA-Board-v0.11.0-<short-sha>.apk` | `.github/workflows/android-test-apk.yml` |
| Artifact 名 | `mydca-android-v0.11.0-<sha>` | 同上 |

- 继续使用一次性 debug 签名（`assembleDebug`），不新增 release signing key，APK 不提交到 Git。
- CI source commit / Run ID / Artifact ID / APK 文件名 / CI APK SHA-256：**待 owner push 后回填**。普通自动任务只提交、不 push，故本轮不声称 CI APK 已交付。

## 8. 与后续工作的关系

- 真机人工验收项：投资草稿的产品选择、资金账户过滤、付款账户 / 待结算应收预览与二次确认文案，以及 v0.10.0 转账、v0.9.0 桌面小组件与 v0.8 分享入口。
- CI APK 证据回填依赖 owner / AiCore 真实推送到 `v2`；普通自动任务只提交、不 push。
- **SELL / REDEMPTION 尚未支持**：本轮只做 BUY / SUBSCRIPTION，卖出 / 赎回为下一阶段独立任务。
- 投资订单确认只生成下单付款账本（CASH CREDIT + RECEIVABLE DEBIT），**尚未 settlement**；最终持仓由后续 SettlementService 生成。
- v0.8 数据库强幂等 migration 仍未部署（与本轮无关，需单独授权并先跑只读数据预检）。
- **不存在任何真实交易自动化**：不自动下单、不自动结算、不生成最终持仓、不调用任何真实交易渠道。