# MyDCA v0.10.0 TRANSFER 转账草稿完整闭环

- **作者**：Codex（AiCore 普通工程任务执行）
- **任务**：`task-mydca-v010-transfer-draft-loop-20260928`
- **工程仓库**：`TimeLordTTY/MyDCA-Board@v2`
- **风险级别 / 授权**：L3，owner 直接批准（`approval_source: direct_owner_chat`）
- **完成时间**：2026-09-28 +08:00

> 本文记录本次任务的当时事实。当前状态与后续计划以 `docs/CURRENT_DEVELOPMENT_STATE.md` 为准。

## 1. 目标

把草稿闭环从 `EXPENSE` / `INCOME` 扩展到 **`TRANSFER` 转账**：

`文本候选或草稿编辑进入 TRANSFER → 明确选择转出账户 + 转入账户 + 金额 → 双账户影响预览 → 主人二次确认 → 后端通过正式账本服务写入一笔转账 → 重复确认不重复写账`

安全链路完全不变：

`输入/解析 → DRAFT → fresh preview → 主人二次确认 → 正式账本`

任何采集、解析、OCR、通知、Outbox 都不允许自动 preview / confirm / 正式入账，也不允许自动交易。

## 2. 交付内容

### 2.1 统一 TRANSFER 候选结构

写入标准 JSON 时只使用下列标准字段：

| 字段 | 含义 |
| --- | --- |
| `txnType` | `TRANSFER` |
| `accountId` | 转出账户 ID |
| `accountNameHint` | 转出账户名称提示 |
| `targetAccountId` | 转入账户 ID |
| `targetAccountNameHint` | 转入账户名称提示 |
| `amount` | 正数金额 |
| `note` | 备注 |

只做兼容**读取**的别名：`sourceAccountId` / `cashAccountId` → `accountId`；`toAccountId` / `destinationAccountId` → `targetAccountId`。写回时一律归一化为标准字段。

同步该约定的类型：后端 `AccountingIntentDTO`、`DraftPreviewDTO`；Android `AccountingIntentDto`、`DraftPreviewDto`；`web/shared` 的 `types/draft.ts`、`types/aiAccounting.ts`。

### 2.2 文本候选识别 TRANSFER（不自动匹配真实账户）

- `AiAccountingService.detectTxnType` 先执行 `detectTransfer`：命中 `转账` / `转到` / `转入` / `转出`，或同时出现 `从` 与 `到` 时判定为 `TRANSFER`。
- TRANSFER 判定优先级**高于** `到账` / `付款` / `支付` 等可能造成 `EXPENSE` / `INCOME` 误判的关键词；`工资到账 5000` 仍为 `INCOME`，`支付午饭 30` 仍为 `EXPENSE`。
- 保守提取：金额；`从 X` 作为 `accountNameHint`；最后一个 `到 Y`（或 `转入 Y`）作为 `targetAccountNameHint`；账户提示读到转账动词或分隔符即停止，不会把「从 A 转到 B」整体当成一个账户名。
- 规则解析**不把名称提示映射为真实账户 ID**：缺失时只生成 DRAFT，并把 `accountId` / `targetAccountId` 写入 `missingFields`，不确定就保持缺失、不猜账户。

### 2.3 后端 TRANSFER 预览（只读）

`DraftLedgerEntryService.buildPreview` 读取转出 / 转入账户、金额与备注，并要求两个账户同时满足：

- 当前 user / family 可见（`selectVisibleRealById`：`is_active=1` 且 `account_kind='REAL'` 且归属作用域匹配）；
- 叶子账户（父账户仅聚合展示，不可记账）；
- 转出 ≠ 转入；
- 币种一致（跨币种转账暂不支持）。

TRANSFER **不套用** EXPENSE 的「日常消费只允许 SPENDABLE」规则：`SPENDABLE ↔ RESERVED ↔ INVESTABLE` 之间允许转移；跨资金用途时 preview 给出中文风险提示（例如「本次会把资金从 RESERVED 转到 SPENDABLE，请确认这是主动调整资金分区。」）。

预览新增字段：`targetAccountId`、`targetAccountName`、`targetAccountType`、`targetFundUsage`、`targetAccountDelta`。
TRANSFER 影响口径：`accountDelta = -amount`（`impactDirection = DECREASE`），`targetAccountDelta = +amount`。

预览阶段只读：不调用 `LedgerService` / `QuickEntryService`，不写正式账本。

### 2.4 `QuickEntryService.quickTransfer`

新增明确转账入口：

`quickTransfer(userId, familyId, sourceAccountId, targetAccountId, amount, note)`

- 不使用「查到 ID 就能记账」的逻辑：两个账户都必须当前 user / family 可见、为启用的 REAL 叶子账户，且转出 ≠ 转入、币种一致、金额大于 0；任一校验不通过直接拒绝，由事务整体回滚。
- 通过既有 `LedgerService.createTransaction(userId, familyId, "TRANSFER_OUT", null, postings, note)` 创建**一笔**平衡业务交易：转出账户 `CREDIT`、转入账户 `DEBIT`。
- 流水页仍按既有转账语义展示转出 / 转入两条视图，但底层只有一笔交易，不制造重复账；转账不计入收入 / 支出净现金流。
- 不新增数据库表、不新增 migration。

### 2.5 草稿 confirm 扩展

- `confirmDraft` 对 TRANSFER **重新 `buildPreview`**，不信任草稿里旧 preview JSON；只有 `preview.confirmSupported = true` 才调用 `quickTransfer`。
- 确认后把正式 `txnId` 写回既有 `confirmTxnId`；`DraftLedgerEntryMapper.markConfirmed` 仍只匹配 `status='DRAFT'`。
- 同一 `CONFIRMED` 草稿第二次确认直接幂等返回，不创建第二笔转账；`IGNORED` 草稿仍不可确认。
- 不存在「parse 后自动 confirm」或「创建 DRAFT 后自动 confirm」。

### 2.6 Android v0.10.0 草稿编辑体验

- 交易类型选择扩展为「支出 EXPENSE / 收入 INCOME / 转账 TRANSFER」；切回 EXPENSE / INCOME 时清空目标账户，非转账 payload 不残留 `targetAccountId`。
- TRANSFER 表单显示：转出账户、转入账户、金额、备注；两边只能选真实可记账叶子账户，转出 / 转入不能相同；币种不一致前端提前提示，最终以后端 preview 为准。
- 不因 `RESERVED` / `INVESTABLE` 阻断转账（`DraftAccountSelection.TRANSFER` 只要求真实可记账叶子账户）。
- TRANSFER 预览中文展示：从（账户名 / 资金用途）、到（账户名 / 资金用途）、金额、转出账户变动、转入账户变动、风险提示。
- 确认弹窗标题为「确认将 ¥X 从 A 转到 B？」，不再使用通用「确认正式记账？」。
- 保存后仍清空旧 preview；只有「保存并预览 → `preview.confirmSupported=true`」后确认按钮才可用。

### 2.7 PC / shared 最小兼容

- `web/shared` 同步 TRANSFER 与 target 字段类型，避免后端新增字段成为类型盲区。
- PC 草稿箱：类型标签新增「转账」，可安全查看 / 预览 TRANSFER 的双账户与双 delta（转出 / 转入影响卡），不再把 TRANSFER 显示成不支持类型；同时补齐最小安全编辑（类型可选转账、转入账户选择、相同账户与缺转入账户的保存前校验、非转账不写 target 字段）。

## 3. 明确不做（安全边界）

- 不连接生产数据库、不执行 v0.8 migration、不新增表结构；测试全部使用 mock，不读写任何真实财务记录。
- 不自动 preview、不自动 confirm、不自动转账、不自动交易；OCR / 通知 / 分享 / Widget 入口不获得任何转账能力。
- 不绕过当前 user / family 可见性检查，不支持跨币种转账。
- 不重构 PC 转账编辑 UI（只做最小安全补齐）。
- 投资订单类草稿确认、结算与持仓影响仍为后续独立任务。

## 4. 变更清单

后端（`backend/**`）：

- `dto/AccountingIntentDTO.java`：新增 `targetAccountId`、`targetAccountNameHint`；`txnType` 说明扩展为 EXPENSE / INCOME / TRANSFER。
- `dto/DraftPreviewDTO.java`：新增 `targetAccountId`、`targetAccountName`、`targetAccountType`、`targetFundUsage`、`targetAccountDelta`。
- `service/AiAccountingService.java`：`detectTransfer` 优先判定；转出 / 转入名称提示提取；`missingFields` 增加 `targetAccountId`；`toIntentJson` / `normalizeIntent` 写回 target 字段。
- `service/DraftLedgerEntryService.java`：预览支持 TRANSFER（双账户校验、跨用途提示、双 delta、支持类型与文案）；`confirmDraft` 走 `quickTransfer`。
- `service/QuickEntryService.java`：新增 `quickTransfer` 与币种比较。
- 测试：`AiAccountingServiceTest`、`DraftLedgerEntryServiceTest` 扩展，新增 `QuickEntryServiceTransferTest`、`LedgerServiceTransferStatsTest`。

Android（`android-app/**`）：

- `data/dto/AiAccountingDtos.kt`、`data/dto/DraftDtos.kt`：新增 target 字段。
- `ui/state/DraftAccountSelection.kt`：新增 `TRANSFER` 与转账前端提示。
- `ui/state/DraftEditState.kt`：表单新增 `targetAccountId`；归一化读写与别名兼容；非转账不写 target。
- `ui/state/DraftReview.kt`：转账影响行、确认弹窗标题 / 正文。
- `ui/screens/DraftInboxScreen.kt`：转账类型按钮、双账户选择、转账预览行、确认弹窗标题。
- `app/build.gradle.kts`：`versionCode 10 → 11`、`versionName 0.9.0 → 0.10.0`。

Web（`web/**`）：

- `shared/src/types/draft.ts`、`shared/src/types/aiAccounting.ts`、`shared/src/api/draft.ts`：TRANSFER 与 target 字段。
- `pc-app/src/views/DraftInbox.vue`：转账类型 / 双账户预览与实际安全编辑。

工作流与文档：`.github/workflows/android-test-apk.yml`（v0.10.0 命名）、`README.md`、`android-app/README.md`、`docs/CURRENT_DEVELOPMENT_STATE.md`、`docs/Phase3-开发进度总结.md`、`docs/Phase3-移动端原生App与自动记账.md`、`docs/DOCUMENT_INDEX.md`、本文件。

## 5. 测试矩阵（任务要求 → 证据）

后端（`mvn -B test`）：

| 任务要求 | 证据 |
| --- | --- |
| 1. TRANSFER 缺 source → 不可确认 | `DraftLedgerEntryServiceTest` 转账缺转出账户用例 |
| 2. 缺 target → 不可确认 | `DraftLedgerEntryServiceTest` 转账缺转入账户用例 |
| 3. source == target → 阻断 | `DraftLedgerEntryServiceTest` 转出等于转入用例 |
| 4. source 不可见 / 非 REAL / 父账户 → 阻断 | `DraftLedgerEntryServiceTest` 转出账户不可见 / 父账户用例 |
| 5. target 不可见 / 非 REAL / 父账户 → 阻断 | `DraftLedgerEntryServiceTest` 转入账户不可见 / 父账户用例 |
| 6. 不同币种 → 阻断 | `DraftLedgerEntryServiceTest` 币种不一致用例 |
| 7. RESERVED → SPENDABLE 可转但有风险提示 | `DraftLedgerEntryServiceTest` 跨资金用途用例 |
| 8. INVESTABLE → RESERVED 可转但有风险提示 | `DraftLedgerEntryServiceTest` 跨资金用途用例 |
| 9. preview 只读，不调用 LedgerService | `DraftLedgerEntryServiceTest` 预览路径 `verify(ledgerService, never())` |
| 10. confirm 创建一笔平衡转账 | `QuickEntryServiceTransferTest` 平衡双分录用例 |
| 11. 第二次 confirm 不重复建账 | `DraftLedgerEntryServiceTest` 转账幂等重放用例 |
| 12. confirm 失败事务回滚，草稿仍 DRAFT | `DraftLedgerEntryServiceTest` 标记确认失败回滚用例 |
| 13. TRANSFER 不计入收入 / 支出统计 | `LedgerServiceTransferStatsTest` |
| 14. EXPENSE / INCOME 回归 | `DraftLedgerEntryServiceTest`、`AiAccountingServiceTest`、`DraftLedgerEntryConcurrentReplayTest` 全量通过 |

文本解析（`AiAccountingServiceTest`）：

| 任务要求 | 证据 |
| --- | --- |
| 「从余额宝转到银行卡 500」 | TRANSFER + `amount=500` + 双名称提示 + `accountId` / `targetAccountId` 缺失 |
| 「转账 1000」 | TRANSFER + `amount=1000` + 双账户缺失 |
| 「工资到账 5000」仍为 INCOME | 收入用例回归通过 |
| 「支付午饭 30」仍为 EXPENSE | 支出用例回归通过 |

Android（`gradlew.bat --no-daemon testDebugUnitTest`）：

| 任务要求 | 证据 |
| --- | --- |
| TRANSFER 表单双账户 | `DraftEditStateTest`（`targetAccountId` 读写与别名兼容） |
| 两账户相同前端阻断 | `DraftAccountSelectionTest` 转账提示用例 |
| 切回 EXPENSE / INCOME 后 target 不残留 | `DraftEditStateTest` payload 归一化用例 |
| 保存后旧 preview 失效 | 既有草稿箱保存路径回归用例 |
| preview 双 delta 展示 | `DraftReviewTest` 转账影响行用例 |
| confirm 受 fresh preview gate 控制 | 既有 confirm gating 用例回归 |
| confirm dialog 中文明确 from/to/amount | `DraftReviewTest` 确认弹窗标题用例 |
| v0.7 / v0.8 / v0.9 导航、OCR、Outbox、Widget 回归 | 全量 35 个测试类 206 项通过 |

## 6. 验证与结果

| 验证 | 命令 | 结果 |
| --- | --- | --- |
| 后端测试 | `backend: mvn -B test` | 通过：13 个测试类共 95 项，0 失败 / 0 错误 / 0 跳过（由 70 项增至 95 项） |
| Android 单元测试 | `android-app: gradlew.bat --no-daemon testDebugUnitTest` | 通过：35 个测试类共 206 项，0 失败 / 0 错误 / 0 跳过（由 195 项增至 206 项） |
| Android 打包 | `android-app: gradlew.bat --no-daemon assembleDebug` | 通过 |
| Android 静态检查 | `android-app: gradlew.bat --no-daemon lintDebug` | 通过：0 error，2 条既有 warning（`DataExtractionRules`、`ObsoleteSdkInt`，非本轮引入） |
| 仓库编译钩子 | `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1` | 通过（后端 `mvn -DskipTests package` + 前端 `npm run build`），成功静默、无 Beep / Toast / Popup |
| 补丁卫生 | `git diff --check` | 通过，无空白错误 |

- 本地 Debug APK（本机观察值，不是制品身份）：55,821,214 bytes，SHA-256 `4D5C1443EE30B0A8315ECBB848A3FFAD62ED3B675861236361B8971552C087DB`；APK 不提交到 Git。
- debug APK 本地字节不可复现（同一份源码连续两次 `assembleDebug` 的体积与 SHA-256 都会变化），上面的本机观察值与制品身份无关。

## 7. 版本与制品

| 项 | 值 | 证据 |
| --- | --- | --- |
| `versionName` | `0.10.0`（由 `0.9.0` 升级） | `android-app/app/build.gradle.kts` |
| `versionCode` | `11`（由 `10` 递增） | 同上 |
| APK 文件命名 | `MyDCA-Board-v0.10.0-<short-sha>.apk` | `.github/workflows/android-test-apk.yml` |
| Artifact 名 | `mydca-android-v0.10.0-<sha>` | 同上 |

- 继续使用一次性 debug 签名（`assembleDebug`），不新增 release signing key，APK 不提交到 Git。
- CI source commit：`e5c544db7d90f82858ddc03a1ca285ec7659e037`
- GitHub Actions `Android test APK`：Run `36369966197`，结论 `success`
- Artifact ID：`10949105553`
- Artifact：`mydca-android-v0.10.0-e5c544db7d90f82858ddc03a1ca285ec7659e037`
- APK：`MyDCA-Board-v0.10.0-e5c544db.apk`
- CI APK SHA-256：`FD39508EA408807DB5ECEEBAFD2B2F4630D766447398E29D1397D8721A5304F0`
- 该 SHA-256 来自 CI Artifact 内 `SHA256SUMS.txt`，不是本地 debug APK 哈希。

## 8. 与后续工作的关系

- 真机人工验收项：转账双账户选择、确认弹窗文案、正式转账后流水页的转出 / 转入两条视图，以及 v0.9.0 桌面小组件与 v0.8 分享入口。
- CI APK 证据回填依赖 owner / AiCore 真实推送到 `v2`；普通自动任务只提交、不 push。
- v0.8 数据库强幂等 migration 仍未部署（与本轮无关，需单独授权并先跑只读数据预检）。
- 投资订单类草稿确认、完整结算 / 持仓影响、策略与回测闭环继续按设计推进。
