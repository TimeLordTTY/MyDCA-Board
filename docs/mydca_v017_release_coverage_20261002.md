# v0.17 Mapper / SQL 枚举与覆盖附录

目标：`d248102f0e442ae5e6740a9db40d3efc6a95f873`；基线：`9535d69faa4c5e13d76e5718d740fb3c8c607377`。本地只读枚举，未刷新远端、未执行 SQL。

全量语法枚举不代表全仓业务语义审查通过；本次语义检查限定 v0.17 风险观察链路。脚本对象候选包含函数/注释候选，不能当成实际建表数。

## 数量闭环

| 项目 | 数量 |
| --- | --- |
| Mapper文件总数 | 22 |
| XML Mapper文件数 | 20 |
| 注解Mapper文件数 | 2 |
| Mapper SQL语句总数 | 151 |
| Mapper引用数据库对象总数 | 25 |
| SQL脚本文件总数 | 36 |
| 脚本涉及数据库对象总数 | 63 |
| 有脚本覆盖的Mapper对象数 | 25 |
| 无仓库脚本覆盖的Mapper对象数 | 0 |
| 变更Mapper引用但无脚本覆盖的对象数 | 0 |
| 本分支新增引用数据库对象数 | 4 |
| 本分支新增引用但无脚本覆盖的对象数 | 0 |
| 仅非初始化脚本存在建表但通用初始化缺失对象数 | 1 |
| 未完整解析Mapper数 | 0 |
| 动态表名引用数 | 0 |
| 数量闭环 | True |

Mapper 对象 25 = 有脚本覆盖 25 + 无脚本覆盖 0。新增风险对象 4，全部具有初始化及增量脚本。

## 逐对象覆盖矩阵

| 对象 | Mapper | 通用初始化建表 | 其他脚本 |
| --- | --- | --- | --- |
| accounts | backend/src/main/resources/mapper/AccountMapper.xml<br>backend/src/main/resources/mapper/LedgerPostingMapper.xml<br>backend/src/main/resources/mapper/LedgerTxnMapper.xml | sql/initsql/DDL.sql | sql/V1/DDL.sql<br>sql/V1/DML.sql<br>sql/updatesql/20260113/01_allow_fund_usage_null_for_credit_accounts.sql<br>sql/updatesql/20260113/02_fix_virtual_account_subtype.sql<br>sql/updatesql/20260121/01_add_linked_product_id_to_accounts.sql<br>sql/updatesql/20260122/01_add_mmf_shares_fields.sql<br>sql/updatesql/20260205/01_fix_xiaohe_holding.sql |
| broker_fee_config | backend/src/main/resources/mapper/BrokerFeeConfigMapper.xml | sql/initsql/DDL.sql | sql/updatesql/20260114/03_add_broker_fee_config_table.sql<br>sql/updatesql/20260114/04_init_huabao_broker_fee_example.sql<br>sql/updatesql/20260114/06_update_fee_rate_precision.sql |
| draft_ledger_entry | backend/src/main/resources/mapper/DraftLedgerEntryMapper.xml | 缺失 | sql/updatesql/20260610/01_create_draft_ledger_entry.sql<br>sql/updatesql/20260927/02_normalize_blank_draft_ledger_source_ref.sql<br>sql/updatesql/20260927/03_add_draft_ledger_source_unique_keys.sql |
| draft_lifecycle_event | backend/src/main/resources/mapper/DraftLifecycleEventMapper.xml | sql/initsql/20260928_draft_lifecycle_event.sql | sql/updatesql/20260928/01_create_draft_lifecycle_event.sql |
| families | backend/src/main/resources/mapper/FamilyMapper.xml | sql/initsql/DDL.sql | sql/initsql/DML.sql<br>sql/updatesql/20260111/01_init_admin_user.sql |
| fund_sell_fee_tier | backend/src/main/resources/mapper/FundSellFeeTierMapper.xml | sql/initsql/DDL.sql | sql/updatesql/20260114/05_add_fund_sell_fee_tier_table.sql<br>sql/updatesql/20260114/06_update_fee_rate_precision.sql |
| holdings_snapshot | backend/src/main/resources/mapper/HoldingsSnapshotMapper.xml | sql/initsql/DDL.sql |  |
| indicator_daily | backend/src/main/resources/mapper/IndicatorDailyMapper.xml | sql/initsql/DDL.sql | sql/V1/DDL.sql<br>sql/updatesql/20260604/01_add_boll_kdj_indicator_columns.sql |
| ledger_posting | backend/src/main/resources/mapper/LedgerPostingMapper.xml<br>backend/src/main/resources/mapper/LedgerTxnMapper.xml | sql/initsql/DDL.sql | sql/updatesql/20260114/01_add_posting_balance_fields.sql<br>sql/updatesql/20260131/01_fix_amount_precision.sql |
| ledger_txn | backend/src/main/resources/mapper/LedgerPostingMapper.xml<br>backend/src/main/resources/mapper/LedgerTxnMapper.xml | sql/initsql/DDL.sql | sql/updatesql/20260112/03_add_category_and_reimbursable_fields.sql |
| market_bar_daily | backend/src/main/resources/mapper/MarketBarDailyMapper.xml | sql/initsql/DDL.sql |  |
| market_quote_realtime | backend/src/main/resources/mapper/MarketQuoteRealtimeMapper.xml | sql/initsql/DDL.sql |  |
| nav | backend/src/main/resources/mapper/NavMapper.xml | sql/initsql/DDL.sql | sql/V1/DDL.sql |
| net_worth_snapshot | backend/src/main/resources/mapper/NetWorthSnapshotMapper.xml | sql/initsql/DDL.sql |  |
| order_funding_line | backend/src/main/resources/mapper/OrderFundingLineMapper.xml | sql/initsql/DDL.sql | backend/src/main/resources/db/migration/V2026012801__add_funding_line_type.sql<br>sql/updatesql/20260121/02_add_shares_to_order_funding_line.sql |
| orders | backend/src/main/resources/mapper/OrderFundingLineMapper.xml<br>backend/src/main/resources/mapper/OrderMapper.xml | sql/initsql/DDL.sql | sql/V1/DDL.sql |
| product_master | backend/src/main/resources/mapper/ProductMasterMapper.xml | sql/initsql/DDL.sql | sql/initsql/DML.sql<br>sql/updatesql/20260112/01_init_product_master.sql<br>sql/updatesql/20260112/02_add_product_sort_order.sql<br>sql/updatesql/20260114/02_add_product_note_field.sql<br>sql/updatesql/20260114/06_update_fee_rate_precision.sql |
| research_plan | backend/src/main/java/com/timelordtty/dca/mapper/ResearchPlanMapper.java | backend/sql/initsql/research_plan.sql | backend/migrations/20261001_research_plan.sql |
| risk_watch_event | backend/src/main/java/com/timelordtty/dca/mapper/RiskWatchMapper.java | backend/sql/initsql/risk_alert_history.sql | backend/migrations/20261002_risk_alert_history.sql |
| risk_watch_mute | backend/src/main/java/com/timelordtty/dca/mapper/RiskWatchMapper.java | backend/sql/initsql/risk_alert_history.sql | backend/migrations/20261002_risk_alert_history.sql |
| risk_watch_rule | backend/src/main/java/com/timelordtty/dca/mapper/RiskWatchMapper.java | backend/sql/initsql/risk_watch.sql | backend/migrations/20261002_risk_watch.sql |
| risk_watch_snapshot | backend/src/main/java/com/timelordtty/dca/mapper/RiskWatchMapper.java | backend/sql/initsql/risk_watch.sql | backend/migrations/20261002_risk_watch.sql |
| settlement_confirm | backend/src/main/resources/mapper/SettlementConfirmMapper.xml | sql/initsql/DDL.sql | sql/updatesql/20260929/01_settlement_audit_link.sql |
| user_family_roles | backend/src/main/resources/mapper/UserFamilyRoleMapper.xml | sql/initsql/DDL.sql | sql/initsql/DML.sql<br>sql/updatesql/20260111/01_init_admin_user.sql |
| users | backend/src/main/resources/mapper/UserMapper.xml | sql/initsql/DDL.sql | sql/initsql/DML.sql<br>sql/updatesql/20260111/01_init_admin_user.sql |

既有 `draft_ledger_entry` 仅有增量建表，通用 init 缺失；新安装不能仅运行通用 DDL 后就假定草稿 API 可用。风险模块没有新增此对象依赖。本次未执行或补写 SQL。

## 全部 Mapper 与 SQL 语句

注解 Mapper 以注解和该文件内顺序编号标识；XML 使用 statement ID。

- `backend/src/main/resources/mapper/AccountMapper.xml`（16 条）：`selectById`, `selectByIds`, `selectByCode`, `selectByOwner`, `selectVisibleRealById`, `selectVirtualAccountsByOwner`, `selectChildren`, `selectLeafAccounts`, `selectByLinkedProduct`, `selectByLinkedProductId`, `selectAllLinkedAccounts`, `insert`, `update`, `updateBalance`, `updateReservedAmount`, `updateInitialShares`
- `backend/src/main/resources/mapper/BrokerFeeConfigMapper.xml`（6 条）：`selectById`, `selectByAccountAndRuleType`, `selectByAccountId`, `insert`, `update`, `deleteById`
- `backend/src/main/resources/mapper/DraftLedgerEntryMapper.xml`（12 条）：`VisibleCondition`, `insert`, `selectVisibleById`, `selectVisibleBySource`, `selectVisibleByIdForUpdate`, `selectVisibleList`, `countVisibleByStatus`, `updateDraftContent`, `updatePreview`, `markConfirmed`, `markIgnored`, `reopenIgnored`
- `backend/src/main/resources/mapper/DraftLifecycleEventMapper.xml`（2 条）：`insert`, `selectByDraftId`
- `backend/src/main/resources/mapper/FamilyMapper.xml`（4 条）：`selectById`, `selectByCode`, `insert`, `update`
- `backend/src/main/resources/mapper/FundSellFeeTierMapper.xml`（7 条）：`selectById`, `selectByProductId`, `selectByProductIdAndHoldingDays`, `insert`, `update`, `deleteById`, `deleteByProductId`
- `backend/src/main/resources/mapper/HoldingsSnapshotMapper.xml`（2 条）：`selectByUserAndDate`, `upsert`
- `backend/src/main/resources/mapper/IndicatorDailyMapper.xml`（4 条）：`selectByProductId`, `selectLatest`, `insert`, `update`
- `backend/src/main/resources/mapper/LedgerPostingMapper.xml`（14 条）：`selectByTxnId`, `selectByTxnIds`, `selectByAccountId`, `selectByAccountTypeAndOwner`, `sumDebitByAccount`, `sumCreditByAccount`, `insert`, `batchInsert`, `selectByAccountIdOrderByTxnTime`, `selectByAccountIdsOrderByTxnTime`, `batchUpdateBalanceAfter`, `deleteByTxnId`, `selectDistinctAccountIds`, `selectLatestTxnTimeByAccountIds`
- `backend/src/main/resources/mapper/LedgerTxnMapper.xml`（10 条）：`selectById`, `selectByTxnId`, `selectByOrderId`, `selectByBizGroupKey`, `selectByCondition`, `countByCondition`, `insert`, `update`, `deleteByTxnId`, `selectStatsPostings`
- `backend/src/main/resources/mapper/MarketBarDailyMapper.xml`（4 条）：`selectByProductId`, `selectLatest`, `insert`, `update`
- `backend/src/main/resources/mapper/MarketQuoteRealtimeMapper.xml`（6 条）：`selectByProductIds`, `selectLatest`, `selectHistory`, `deleteByQuoteTimeBefore`, `insert`, `update`
- `backend/src/main/resources/mapper/NavMapper.xml`（5 条）：`selectByProductId`, `selectLatest`, `selectByDate`, `insert`, `update`
- `backend/src/main/resources/mapper/NetWorthSnapshotMapper.xml`（2 条）：`selectByUserAndDate`, `upsert`
- `backend/src/main/resources/mapper/OrderFundingLineMapper.xml`（6 条）：`Base_Column_List`, `selectByOrderId`, `insert`, `batchInsert`, `deleteByOrderId`, `sumPendingSellSharesByAccount`
- `backend/src/main/resources/mapper/OrderMapper.xml`（7 条）：`selectById`, `selectByOrderId`, `selectByStatus`, `selectByUserId`, `selectConfirmedByProductId`, `insert`, `update`
- `backend/src/main/resources/mapper/ProductMasterMapper.xml`（7 条）：`selectById`, `selectByCode`, `selectByCodeOnly`, `selectByCondition`, `insert`, `update`, `batchUpdateSortOrder`
- `backend/src/main/resources/mapper/SettlementConfirmMapper.xml`（2 条）：`selectByOrderId`, `insert`
- `backend/src/main/resources/mapper/UserFamilyRoleMapper.xml`（7 条）：`selectByUserId`, `selectByFamilyId`, `selectRole`, `countAdmins`, `updateRole`, `insert`, `delete`
- `backend/src/main/resources/mapper/UserMapper.xml`（8 条）：`selectById`, `selectByUsername`, `selectActiveUserIds`, `insert`, `update`, `updateProfile`, `updatePasswordHash`, `updateLastLoginAt`
- `backend/src/main/java/com/timelordtty/dca/mapper/ResearchPlanMapper.java`（4 条）：`Insert#1`, `Select#2`, `Select#3`, `Update#4`
- `backend/src/main/java/com/timelordtty/dca/mapper/RiskWatchMapper.java`（16 条）：`Insert#1`, `Select#2`, `Select#3`, `Update#4`, `Insert#5`, `Select#6`, `Select#7`, `Select#8`, `Insert#9`, `Select#10`, `Select#11`, `Select#12`, `Update#13`, `Update#14`, `Insert#15`, `Select#16`

## 全部 SQL 文件

- `backend/migrations/20261001_research_plan.sql`
- `backend/migrations/20261002_risk_alert_history.sql`
- `backend/migrations/20261002_risk_watch.sql`
- `backend/sql/initsql/research_plan.sql`
- `backend/sql/initsql/risk_alert_history.sql`
- `backend/sql/initsql/risk_watch.sql`
- `backend/src/main/resources/db/migration/V2026012801__add_funding_line_type.sql`
- `sql/V1/DDL.sql`
- `sql/V1/DML.sql`
- `sql/initsql/20260928_draft_lifecycle_event.sql`
- `sql/initsql/DDL.sql`
- `sql/initsql/DML.sql`
- `sql/updatesql/20260111/01_init_admin_user.sql`
- `sql/updatesql/20260112/01_init_product_master.sql`
- `sql/updatesql/20260112/02_add_product_sort_order.sql`
- `sql/updatesql/20260112/03_add_category_and_reimbursable_fields.sql`
- `sql/updatesql/20260113/01_allow_fund_usage_null_for_credit_accounts.sql`
- `sql/updatesql/20260113/02_fix_virtual_account_subtype.sql`
- `sql/updatesql/20260114/01_add_posting_balance_fields.sql`
- `sql/updatesql/20260114/02_add_product_note_field.sql`
- `sql/updatesql/20260114/03_add_broker_fee_config_table.sql`
- `sql/updatesql/20260114/04_init_huabao_broker_fee_example.sql`
- `sql/updatesql/20260114/05_add_fund_sell_fee_tier_table.sql`
- `sql/updatesql/20260114/06_update_fee_rate_precision.sql`
- `sql/updatesql/20260121/01_add_linked_product_id_to_accounts.sql`
- `sql/updatesql/20260121/02_add_shares_to_order_funding_line.sql`
- `sql/updatesql/20260122/01_add_mmf_shares_fields.sql`
- `sql/updatesql/20260131/01_fix_amount_precision.sql`
- `sql/updatesql/20260205/01_fix_xiaohe_holding.sql`
- `sql/updatesql/20260604/01_add_boll_kdj_indicator_columns.sql`
- `sql/updatesql/20260610/01_create_draft_ledger_entry.sql`
- `sql/updatesql/20260927/01_precheck_draft_ledger_source_duplicates.sql`
- `sql/updatesql/20260927/02_normalize_blank_draft_ledger_source_ref.sql`
- `sql/updatesql/20260927/03_add_draft_ledger_source_unique_keys.sql`
- `sql/updatesql/20260928/01_create_draft_lifecycle_event.sql`
- `sql/updatesql/20260929/01_settlement_audit_link.sql`

## v0.17 依赖交付文件清单

共 39 个差异文件；风险 Controller → Service → Mapper、共享 API → PC 页面、Android API → Repository → Compose 的语义证据见主报告。

- `android-app/README.md`
- `android-app/app/src/main/java/com/timelordtty/mydca/data/api/WealthHubApi.kt`
- `android-app/app/src/main/java/com/timelordtty/mydca/data/dto/RiskWatchDto.kt`
- `android-app/app/src/main/java/com/timelordtty/mydca/data/repository/RiskWatchRepository.kt`
- `android-app/app/src/main/java/com/timelordtty/mydca/ui/MyDcaApp.kt`
- `android-app/app/src/main/java/com/timelordtty/mydca/ui/screens/OverviewScreen.kt`
- `android-app/app/src/main/java/com/timelordtty/mydca/ui/screens/RiskWatchScreen.kt`
- `android-app/app/src/main/java/com/timelordtty/mydca/ui/state/RiskWatchState.kt`
- `android-app/app/src/test/java/com/timelordtty/mydca/data/repository/RiskWatchRepositoryTest.kt`
- `android-app/app/src/test/java/com/timelordtty/mydca/data/repository/WealthRepositoryTest.kt`
- `android-app/app/src/test/java/com/timelordtty/mydca/ui/state/TodayTodoStateHolderTest.kt`
- `android-app/app/src/test/java/com/timelordtty/mydca/ui/state/WealthStateHolderTest.kt`
- `backend/README.md`
- `backend/migrations/20261002_risk_alert_history.sql`
- `backend/migrations/20261002_risk_watch.sql`
- `backend/sql/initsql/risk_alert_history.sql`
- `backend/sql/initsql/risk_watch.sql`
- `backend/src/main/java/com/timelordtty/dca/controller/RiskWatchController.java`
- `backend/src/main/java/com/timelordtty/dca/dto/RiskWatchDTO.java`
- `backend/src/main/java/com/timelordtty/dca/mapper/RiskWatchMapper.java`
- `backend/src/main/java/com/timelordtty/dca/service/RiskWatchService.java`
- `backend/src/test/java/com/timelordtty/dca/service/RiskWatchServiceTest.java`
- `docs/CURRENT_DEVELOPMENT_STATE.md`
- `docs/DOCUMENT_INDEX.md`
- `docs/mydca_v017_risk_alert_history_20261002.md`
- `docs/mydca_v017_risk_center_android_20261002.md`
- `docs/mydca_v017_risk_center_pc_20261002.md`
- `docs/mydca_v017_risk_watch_backend_20261002.md`
- `web/pc-app/src/components/FinanceRadar.vue`
- `web/pc-app/src/components/ResearchWorkbench.vue`
- `web/pc-app/src/components/riskWatchModel.ts`
- `web/pc-app/src/layouts/MainLayout.vue`
- `web/pc-app/src/router/index.ts`
- `web/pc-app/src/views/RiskCenter.vue`
- `web/pc-app/tests/riskCenter.test.mjs`
- `web/shared/src/api/index.ts`
- `web/shared/src/api/riskWatch.ts`
- `web/shared/src/types/index.ts`
- `web/shared/src/types/riskWatch.ts`
