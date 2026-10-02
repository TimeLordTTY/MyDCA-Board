# v0.18 发布覆盖附录

2026-10-02；只读本地枚举，无数据库连接或脚本执行。依赖范围 `a10a092069295e9b2a83e9792162c65c257ed0a5..18f28879405fd5da9cc86e023be3696881e7ed32`，34 个文件；另复核本任务工作树变更。未刷新远端。

数据库枚举使用 WORKTREE 无基线，只表示最终树统计；新增对象依据依赖范围确定为 allocation_policy，不能把无基线脚本中的“本分支新增”当作事实。全量枚举不代表全仓业务语义审核。64 个脚本对象候选包含函数/注释，不等于表数。

## 数量闭环

| 项目 | 数量 |
| --- | --- |
| Mapper文件总数 | 23 |
| XML Mapper文件数 | 20 |
| 注解Mapper文件数 | 3 |
| Mapper SQL语句总数 | 155 |
| Mapper引用数据库对象总数 | 26 |
| SQL脚本文件总数 | 38 |
| 脚本涉及数据库对象总数 | 64 |
| 有脚本覆盖的Mapper对象数 | 26 |
| 无仓库脚本覆盖的Mapper对象数 | 0 |
| 变更Mapper引用但无脚本覆盖的对象数 | 0 |
| 仅非初始化脚本存在建表但通用初始化缺失对象数 | 1 |
| 未完整解析Mapper数 | 0 |
| 动态表名引用数 | 0 |
| 数量闭环 | True |

26 个 Mapper 对象 = 有脚本覆盖 26 + 无脚本覆盖 0；未解析与动态表名均 0。既有 draft_ledger_entry 仅增量建表，通用 init 缺失；本次 allocation_policy 有等价初始化与增量脚本，无独立回滚/客户脚本。支持 MySQL，不宣称其他方言可用。

## 依赖范围全部文件

| 文件 | 分类 |
| --- | --- |
| `android-app/ALLOCATION_VIEW.md` | 其他 |
| `android-app/app/src/main/java/com/timelordtty/mydca/data/api/WealthHubApi.kt` | 代码 |
| `android-app/app/src/main/java/com/timelordtty/mydca/data/dto/AllocationDto.kt` | 代码 |
| `android-app/app/src/main/java/com/timelordtty/mydca/data/repository/AllocationRepository.kt` | 代码 |
| `android-app/app/src/main/java/com/timelordtty/mydca/ui/MyDcaApp.kt` | 代码 |
| `android-app/app/src/main/java/com/timelordtty/mydca/ui/screens/AllocationScreen.kt` | 代码 |
| `android-app/app/src/main/java/com/timelordtty/mydca/ui/screens/OverviewScreen.kt` | 代码 |
| `android-app/app/src/main/java/com/timelordtty/mydca/ui/state/AllocationViewState.kt` | 代码 |
| `android-app/app/src/test/java/com/timelordtty/mydca/data/repository/AllocationRepositoryTest.kt` | 测试 |
| `android-app/app/src/test/java/com/timelordtty/mydca/data/repository/WealthRepositoryTest.kt` | 测试 |
| `android-app/app/src/test/java/com/timelordtty/mydca/ui/state/TodayTodoStateHolderTest.kt` | 测试 |
| `android-app/app/src/test/java/com/timelordtty/mydca/ui/state/WealthStateHolderTest.kt` | 测试 |
| `backend/ALLOCATION_POLICY_API.md` | 其他 |
| `backend/REBALANCE_PREVIEW_API.md` | 其他 |
| `backend/migrations/20261002_allocation_policy.sql` | 脚本 |
| `backend/sql/initsql/allocation_policy.sql` | 脚本 |
| `backend/src/main/java/com/timelordtty/dca/controller/AllocationPolicyController.java` | 代码 |
| `backend/src/main/java/com/timelordtty/dca/dto/AllocationPolicyDTO.java` | 代码 |
| `backend/src/main/java/com/timelordtty/dca/dto/RebalancePreviewDTO.java` | 代码 |
| `backend/src/main/java/com/timelordtty/dca/mapper/AllocationPolicyMapper.java` | 代码 |
| `backend/src/main/java/com/timelordtty/dca/service/AllocationPolicyService.java` | 代码 |
| `backend/src/main/java/com/timelordtty/dca/service/RebalancePreviewEngine.java` | 代码 |
| `backend/src/test/java/com/timelordtty/dca/service/AllocationPolicyServiceTest.java` | 测试 |
| `backend/src/test/java/com/timelordtty/dca/service/RebalancePreviewEngineTest.java` | 测试 |
| `web/pc-app/src/components/allocationPolicyModel.ts` | 代码 |
| `web/pc-app/src/layouts/MainLayout.vue` | 代码 |
| `web/pc-app/src/router/index.ts` | 代码 |
| `web/pc-app/src/views/RebalanceCenter.vue` | 代码 |
| `web/pc-app/tests/rebalanceCenter.test.mjs` | 测试 |
| `web/pc-app/tests/tsconfig.allocation.json` | 测试 |
| `web/shared/src/api/allocationPolicy.ts` | 代码 |
| `web/shared/src/api/index.ts` | 代码 |
| `web/shared/src/types/allocationPolicy.ts` | 代码 |
| `web/shared/src/types/index.ts` | 代码 |

## 全部 Mapper 与 SQL 语句

| Mapper | 语句 ID / 注解顺序 | 类型 | 对象与操作 |
| --- | --- | --- | --- |
| `backend/src/main/resources/mapper/AccountMapper.xml` | `selectById` | select | accounts / 查询 |
| `backend/src/main/resources/mapper/AccountMapper.xml` | `selectByIds` | select | accounts / 查询 |
| `backend/src/main/resources/mapper/AccountMapper.xml` | `selectByCode` | select | accounts / 查询 |
| `backend/src/main/resources/mapper/AccountMapper.xml` | `selectByOwner` | select | accounts / 查询 |
| `backend/src/main/resources/mapper/AccountMapper.xml` | `selectVisibleRealById` | select | accounts / 查询 |
| `backend/src/main/resources/mapper/AccountMapper.xml` | `selectVirtualAccountsByOwner` | select | accounts / 查询 |
| `backend/src/main/resources/mapper/AccountMapper.xml` | `selectChildren` | select | accounts / 查询 |
| `backend/src/main/resources/mapper/AccountMapper.xml` | `selectLeafAccounts` | select | accounts / 查询 |
| `backend/src/main/resources/mapper/AccountMapper.xml` | `selectByLinkedProduct` | select | accounts / 查询 |
| `backend/src/main/resources/mapper/AccountMapper.xml` | `selectByLinkedProductId` | select | accounts / 查询 |
| `backend/src/main/resources/mapper/AccountMapper.xml` | `selectAllLinkedAccounts` | select | accounts / 查询 |
| `backend/src/main/resources/mapper/AccountMapper.xml` | `insert` | insert | accounts / 新增 |
| `backend/src/main/resources/mapper/AccountMapper.xml` | `update` | update | accounts / 修改 |
| `backend/src/main/resources/mapper/AccountMapper.xml` | `updateBalance` | update | accounts / 修改 |
| `backend/src/main/resources/mapper/AccountMapper.xml` | `updateReservedAmount` | update | accounts / 修改 |
| `backend/src/main/resources/mapper/AccountMapper.xml` | `updateInitialShares` | update | accounts / 修改 |
| `backend/src/main/resources/mapper/BrokerFeeConfigMapper.xml` | `selectById` | select | broker_fee_config / 查询 |
| `backend/src/main/resources/mapper/BrokerFeeConfigMapper.xml` | `selectByAccountAndRuleType` | select | broker_fee_config / 查询 |
| `backend/src/main/resources/mapper/BrokerFeeConfigMapper.xml` | `selectByAccountId` | select | broker_fee_config / 查询 |
| `backend/src/main/resources/mapper/BrokerFeeConfigMapper.xml` | `insert` | insert | broker_fee_config / 新增 |
| `backend/src/main/resources/mapper/BrokerFeeConfigMapper.xml` | `update` | update | broker_fee_config / 修改 |
| `backend/src/main/resources/mapper/BrokerFeeConfigMapper.xml` | `deleteById` | delete | broker_fee_config / 查询; broker_fee_config / 删除 |
| `backend/src/main/resources/mapper/DraftLedgerEntryMapper.xml` | `VisibleCondition` | sql |  |
| `backend/src/main/resources/mapper/DraftLedgerEntryMapper.xml` | `insert` | insert | draft_ledger_entry / 新增 |
| `backend/src/main/resources/mapper/DraftLedgerEntryMapper.xml` | `selectVisibleById` | select | draft_ledger_entry / 查询 |
| `backend/src/main/resources/mapper/DraftLedgerEntryMapper.xml` | `selectVisibleBySource` | select | draft_ledger_entry / 查询 |
| `backend/src/main/resources/mapper/DraftLedgerEntryMapper.xml` | `selectVisibleByIdForUpdate` | select | draft_ledger_entry / 查询 |
| `backend/src/main/resources/mapper/DraftLedgerEntryMapper.xml` | `selectVisibleList` | select | draft_ledger_entry / 查询 |
| `backend/src/main/resources/mapper/DraftLedgerEntryMapper.xml` | `countVisibleByStatus` | select | draft_ledger_entry / 查询 |
| `backend/src/main/resources/mapper/DraftLedgerEntryMapper.xml` | `updateDraftContent` | update | draft_ledger_entry / 修改 |
| `backend/src/main/resources/mapper/DraftLedgerEntryMapper.xml` | `updatePreview` | update | draft_ledger_entry / 修改 |
| `backend/src/main/resources/mapper/DraftLedgerEntryMapper.xml` | `markConfirmed` | update | draft_ledger_entry / 修改 |
| `backend/src/main/resources/mapper/DraftLedgerEntryMapper.xml` | `markIgnored` | update | draft_ledger_entry / 修改 |
| `backend/src/main/resources/mapper/DraftLedgerEntryMapper.xml` | `reopenIgnored` | update | draft_ledger_entry / 修改 |
| `backend/src/main/resources/mapper/DraftLifecycleEventMapper.xml` | `insert` | insert | draft_lifecycle_event / 新增 |
| `backend/src/main/resources/mapper/DraftLifecycleEventMapper.xml` | `selectByDraftId` | select | draft_lifecycle_event / 查询 |
| `backend/src/main/resources/mapper/FamilyMapper.xml` | `selectById` | select | families / 查询 |
| `backend/src/main/resources/mapper/FamilyMapper.xml` | `selectByCode` | select | families / 查询 |
| `backend/src/main/resources/mapper/FamilyMapper.xml` | `insert` | insert | families / 新增 |
| `backend/src/main/resources/mapper/FamilyMapper.xml` | `update` | update | families / 修改 |
| `backend/src/main/resources/mapper/FundSellFeeTierMapper.xml` | `selectById` | select | fund_sell_fee_tier / 查询 |
| `backend/src/main/resources/mapper/FundSellFeeTierMapper.xml` | `selectByProductId` | select | fund_sell_fee_tier / 查询 |
| `backend/src/main/resources/mapper/FundSellFeeTierMapper.xml` | `selectByProductIdAndHoldingDays` | select | fund_sell_fee_tier / 查询 |
| `backend/src/main/resources/mapper/FundSellFeeTierMapper.xml` | `insert` | insert | fund_sell_fee_tier / 新增 |
| `backend/src/main/resources/mapper/FundSellFeeTierMapper.xml` | `update` | update | fund_sell_fee_tier / 修改 |
| `backend/src/main/resources/mapper/FundSellFeeTierMapper.xml` | `deleteById` | delete | fund_sell_fee_tier / 查询; fund_sell_fee_tier / 删除 |
| `backend/src/main/resources/mapper/FundSellFeeTierMapper.xml` | `deleteByProductId` | delete | fund_sell_fee_tier / 查询; fund_sell_fee_tier / 删除 |
| `backend/src/main/resources/mapper/HoldingsSnapshotMapper.xml` | `selectByUserAndDate` | select | holdings_snapshot / 查询 |
| `backend/src/main/resources/mapper/HoldingsSnapshotMapper.xml` | `upsert` | insert | holdings_snapshot / 新增 |
| `backend/src/main/resources/mapper/IndicatorDailyMapper.xml` | `selectByProductId` | select | indicator_daily / 查询 |
| `backend/src/main/resources/mapper/IndicatorDailyMapper.xml` | `selectLatest` | select | indicator_daily / 查询 |
| `backend/src/main/resources/mapper/IndicatorDailyMapper.xml` | `insert` | insert | indicator_daily / 新增 |
| `backend/src/main/resources/mapper/IndicatorDailyMapper.xml` | `update` | update | indicator_daily / 修改 |
| `backend/src/main/resources/mapper/LedgerPostingMapper.xml` | `selectByTxnId` | select | ledger_posting / 查询 |
| `backend/src/main/resources/mapper/LedgerPostingMapper.xml` | `selectByTxnIds` | select | ledger_posting / 查询 |
| `backend/src/main/resources/mapper/LedgerPostingMapper.xml` | `selectByAccountId` | select | ledger_posting / 查询 |
| `backend/src/main/resources/mapper/LedgerPostingMapper.xml` | `selectByAccountTypeAndOwner` | select | ledger_posting / 查询; accounts / 查询 |
| `backend/src/main/resources/mapper/LedgerPostingMapper.xml` | `sumDebitByAccount` | select | ledger_posting / 查询 |
| `backend/src/main/resources/mapper/LedgerPostingMapper.xml` | `sumCreditByAccount` | select | ledger_posting / 查询 |
| `backend/src/main/resources/mapper/LedgerPostingMapper.xml` | `insert` | insert | ledger_posting / 新增 |
| `backend/src/main/resources/mapper/LedgerPostingMapper.xml` | `batchInsert` | insert | ledger_posting / 新增 |
| `backend/src/main/resources/mapper/LedgerPostingMapper.xml` | `selectByAccountIdOrderByTxnTime` | select | ledger_posting / 查询; ledger_txn / 查询 |
| `backend/src/main/resources/mapper/LedgerPostingMapper.xml` | `selectByAccountIdsOrderByTxnTime` | select | ledger_posting / 查询; ledger_txn / 查询 |
| `backend/src/main/resources/mapper/LedgerPostingMapper.xml` | `batchUpdateBalanceAfter` | update | ledger_posting / 修改 |
| `backend/src/main/resources/mapper/LedgerPostingMapper.xml` | `deleteByTxnId` | delete | ledger_posting / 查询; ledger_posting / 删除 |
| `backend/src/main/resources/mapper/LedgerPostingMapper.xml` | `selectDistinctAccountIds` | select | ledger_posting / 查询 |
| `backend/src/main/resources/mapper/LedgerPostingMapper.xml` | `selectLatestTxnTimeByAccountIds` | select | ledger_posting / 查询; ledger_txn / 查询 |
| `backend/src/main/resources/mapper/LedgerTxnMapper.xml` | `selectById` | select | ledger_txn / 查询 |
| `backend/src/main/resources/mapper/LedgerTxnMapper.xml` | `selectByTxnId` | select | ledger_txn / 查询 |
| `backend/src/main/resources/mapper/LedgerTxnMapper.xml` | `selectByOrderId` | select | ledger_txn / 查询 |
| `backend/src/main/resources/mapper/LedgerTxnMapper.xml` | `selectByBizGroupKey` | select | ledger_txn / 查询 |
| `backend/src/main/resources/mapper/LedgerTxnMapper.xml` | `selectByCondition` | select | ledger_txn / 查询; ledger_posting / 查询; accounts / 查询 |
| `backend/src/main/resources/mapper/LedgerTxnMapper.xml` | `countByCondition` | select | ledger_txn / 查询; ledger_posting / 查询; accounts / 查询 |
| `backend/src/main/resources/mapper/LedgerTxnMapper.xml` | `insert` | insert | ledger_txn / 新增 |
| `backend/src/main/resources/mapper/LedgerTxnMapper.xml` | `update` | update | ledger_txn / 修改 |
| `backend/src/main/resources/mapper/LedgerTxnMapper.xml` | `deleteByTxnId` | delete | ledger_txn / 查询; ledger_txn / 删除 |
| `backend/src/main/resources/mapper/LedgerTxnMapper.xml` | `selectStatsPostings` | select | ledger_txn / 查询; ledger_posting / 查询; accounts / 查询 |
| `backend/src/main/resources/mapper/MarketBarDailyMapper.xml` | `selectByProductId` | select | market_bar_daily / 查询 |
| `backend/src/main/resources/mapper/MarketBarDailyMapper.xml` | `selectLatest` | select | market_bar_daily / 查询 |
| `backend/src/main/resources/mapper/MarketBarDailyMapper.xml` | `insert` | insert | market_bar_daily / 新增 |
| `backend/src/main/resources/mapper/MarketBarDailyMapper.xml` | `update` | update | market_bar_daily / 修改 |
| `backend/src/main/resources/mapper/MarketQuoteRealtimeMapper.xml` | `selectByProductIds` | select | market_quote_realtime / 查询 |
| `backend/src/main/resources/mapper/MarketQuoteRealtimeMapper.xml` | `selectLatest` | select | market_quote_realtime / 查询 |
| `backend/src/main/resources/mapper/MarketQuoteRealtimeMapper.xml` | `selectHistory` | select | market_quote_realtime / 查询 |
| `backend/src/main/resources/mapper/MarketQuoteRealtimeMapper.xml` | `deleteByQuoteTimeBefore` | delete | market_quote_realtime / 查询; market_quote_realtime / 删除 |
| `backend/src/main/resources/mapper/MarketQuoteRealtimeMapper.xml` | `insert` | insert | market_quote_realtime / 新增 |
| `backend/src/main/resources/mapper/MarketQuoteRealtimeMapper.xml` | `update` | update | market_quote_realtime / 修改 |
| `backend/src/main/resources/mapper/NavMapper.xml` | `selectByProductId` | select | nav / 查询 |
| `backend/src/main/resources/mapper/NavMapper.xml` | `selectLatest` | select | nav / 查询 |
| `backend/src/main/resources/mapper/NavMapper.xml` | `selectByDate` | select | nav / 查询 |
| `backend/src/main/resources/mapper/NavMapper.xml` | `insert` | insert | nav / 新增 |
| `backend/src/main/resources/mapper/NavMapper.xml` | `update` | update | nav / 修改 |
| `backend/src/main/resources/mapper/NetWorthSnapshotMapper.xml` | `selectByUserAndDate` | select | net_worth_snapshot / 查询 |
| `backend/src/main/resources/mapper/NetWorthSnapshotMapper.xml` | `upsert` | insert | net_worth_snapshot / 新增 |
| `backend/src/main/resources/mapper/OrderFundingLineMapper.xml` | `Base_Column_List` | sql |  |
| `backend/src/main/resources/mapper/OrderFundingLineMapper.xml` | `selectByOrderId` | select | order_funding_line / 查询 |
| `backend/src/main/resources/mapper/OrderFundingLineMapper.xml` | `insert` | insert | order_funding_line / 新增 |
| `backend/src/main/resources/mapper/OrderFundingLineMapper.xml` | `batchInsert` | insert | order_funding_line / 新增 |
| `backend/src/main/resources/mapper/OrderFundingLineMapper.xml` | `deleteByOrderId` | delete | order_funding_line / 查询; order_funding_line / 删除 |
| `backend/src/main/resources/mapper/OrderFundingLineMapper.xml` | `sumPendingSellSharesByAccount` | select | order_funding_line / 查询; orders / 查询 |
| `backend/src/main/resources/mapper/OrderMapper.xml` | `selectById` | select | orders / 查询 |
| `backend/src/main/resources/mapper/OrderMapper.xml` | `selectByOrderId` | select | orders / 查询 |
| `backend/src/main/resources/mapper/OrderMapper.xml` | `selectByStatus` | select | orders / 查询 |
| `backend/src/main/resources/mapper/OrderMapper.xml` | `selectByUserId` | select | orders / 查询 |
| `backend/src/main/resources/mapper/OrderMapper.xml` | `selectConfirmedByProductId` | select | orders / 查询 |
| `backend/src/main/resources/mapper/OrderMapper.xml` | `insert` | insert | orders / 新增 |
| `backend/src/main/resources/mapper/OrderMapper.xml` | `update` | update | orders / 修改 |
| `backend/src/main/resources/mapper/ProductMasterMapper.xml` | `selectById` | select | product_master / 查询 |
| `backend/src/main/resources/mapper/ProductMasterMapper.xml` | `selectByCode` | select | product_master / 查询 |
| `backend/src/main/resources/mapper/ProductMasterMapper.xml` | `selectByCodeOnly` | select | product_master / 查询 |
| `backend/src/main/resources/mapper/ProductMasterMapper.xml` | `selectByCondition` | select | product_master / 查询 |
| `backend/src/main/resources/mapper/ProductMasterMapper.xml` | `insert` | insert | product_master / 新增 |
| `backend/src/main/resources/mapper/ProductMasterMapper.xml` | `update` | update | product_master / 修改 |
| `backend/src/main/resources/mapper/ProductMasterMapper.xml` | `batchUpdateSortOrder` | update | product_master / 修改 |
| `backend/src/main/resources/mapper/SettlementConfirmMapper.xml` | `selectByOrderId` | select | settlement_confirm / 查询 |
| `backend/src/main/resources/mapper/SettlementConfirmMapper.xml` | `insert` | insert | settlement_confirm / 新增 |
| `backend/src/main/resources/mapper/UserFamilyRoleMapper.xml` | `selectByUserId` | select | user_family_roles / 查询 |
| `backend/src/main/resources/mapper/UserFamilyRoleMapper.xml` | `selectByFamilyId` | select | user_family_roles / 查询 |
| `backend/src/main/resources/mapper/UserFamilyRoleMapper.xml` | `selectRole` | select | user_family_roles / 查询 |
| `backend/src/main/resources/mapper/UserFamilyRoleMapper.xml` | `countAdmins` | select | user_family_roles / 查询 |
| `backend/src/main/resources/mapper/UserFamilyRoleMapper.xml` | `updateRole` | update | user_family_roles / 修改 |
| `backend/src/main/resources/mapper/UserFamilyRoleMapper.xml` | `insert` | insert | user_family_roles / 新增 |
| `backend/src/main/resources/mapper/UserFamilyRoleMapper.xml` | `delete` | delete | user_family_roles / 查询; user_family_roles / 删除 |
| `backend/src/main/resources/mapper/UserMapper.xml` | `selectById` | select | users / 查询 |
| `backend/src/main/resources/mapper/UserMapper.xml` | `selectByUsername` | select | users / 查询 |
| `backend/src/main/resources/mapper/UserMapper.xml` | `selectActiveUserIds` | select | users / 查询 |
| `backend/src/main/resources/mapper/UserMapper.xml` | `insert` | insert | users / 新增 |
| `backend/src/main/resources/mapper/UserMapper.xml` | `update` | update | users / 修改 |
| `backend/src/main/resources/mapper/UserMapper.xml` | `updateProfile` | update | users / 修改 |
| `backend/src/main/resources/mapper/UserMapper.xml` | `updatePasswordHash` | update | users / 修改 |
| `backend/src/main/resources/mapper/UserMapper.xml` | `updateLastLoginAt` | update | users / 修改 |
| `backend/src/main/java/com/timelordtty/dca/mapper/AllocationPolicyMapper.java` | `1` | Insert | allocation_policy / 新增 |
| `backend/src/main/java/com/timelordtty/dca/mapper/AllocationPolicyMapper.java` | `2` | Select | allocation_policy / 查询 |
| `backend/src/main/java/com/timelordtty/dca/mapper/AllocationPolicyMapper.java` | `3` | Select | allocation_policy / 查询 |
| `backend/src/main/java/com/timelordtty/dca/mapper/AllocationPolicyMapper.java` | `4` | Update | allocation_policy / 修改 |
| `backend/src/main/java/com/timelordtty/dca/mapper/ResearchPlanMapper.java` | `1` | Insert | research_plan / 新增 |
| `backend/src/main/java/com/timelordtty/dca/mapper/ResearchPlanMapper.java` | `2` | Select | research_plan / 查询 |
| `backend/src/main/java/com/timelordtty/dca/mapper/ResearchPlanMapper.java` | `3` | Select | research_plan / 查询 |
| `backend/src/main/java/com/timelordtty/dca/mapper/ResearchPlanMapper.java` | `4` | Update | research_plan / 修改 |
| `backend/src/main/java/com/timelordtty/dca/mapper/RiskWatchMapper.java` | `1` | Insert | risk_watch_rule / 新增 |
| `backend/src/main/java/com/timelordtty/dca/mapper/RiskWatchMapper.java` | `2` | Select | risk_watch_rule / 查询 |
| `backend/src/main/java/com/timelordtty/dca/mapper/RiskWatchMapper.java` | `3` | Select | risk_watch_rule / 查询 |
| `backend/src/main/java/com/timelordtty/dca/mapper/RiskWatchMapper.java` | `4` | Update | risk_watch_rule / 修改 |
| `backend/src/main/java/com/timelordtty/dca/mapper/RiskWatchMapper.java` | `5` | Insert | risk_watch_snapshot / 新增 |
| `backend/src/main/java/com/timelordtty/dca/mapper/RiskWatchMapper.java` | `6` | Select | risk_watch_snapshot / 查询 |
| `backend/src/main/java/com/timelordtty/dca/mapper/RiskWatchMapper.java` | `7` | Select | risk_watch_snapshot / 查询 |
| `backend/src/main/java/com/timelordtty/dca/mapper/RiskWatchMapper.java` | `8` | Select | risk_watch_rule / 查询 |
| `backend/src/main/java/com/timelordtty/dca/mapper/RiskWatchMapper.java` | `9` | Insert | risk_watch_event / 新增 |
| `backend/src/main/java/com/timelordtty/dca/mapper/RiskWatchMapper.java` | `10` | Select | risk_watch_event / 查询 |
| `backend/src/main/java/com/timelordtty/dca/mapper/RiskWatchMapper.java` | `11` | Select | risk_watch_event / 查询 |
| `backend/src/main/java/com/timelordtty/dca/mapper/RiskWatchMapper.java` | `12` | Select | risk_watch_event / 查询 |
| `backend/src/main/java/com/timelordtty/dca/mapper/RiskWatchMapper.java` | `13` | Update | risk_watch_event / 修改 |
| `backend/src/main/java/com/timelordtty/dca/mapper/RiskWatchMapper.java` | `14` | Update | risk_watch_event / 修改 |
| `backend/src/main/java/com/timelordtty/dca/mapper/RiskWatchMapper.java` | `15` | Insert | risk_watch_mute / 新增 |
| `backend/src/main/java/com/timelordtty/dca/mapper/RiskWatchMapper.java` | `16` | Select | risk_watch_mute / 查询 |

## 全部 SQL 脚本

| 脚本 | 类型（按路径人工分类） | 对象/动作候选 |
| --- | --- | --- |
| `backend/migrations/20261001_research_plan.sql` | 版本增量 | research_plan / 建表; current_timestamp / 更新数据 |
| `backend/migrations/20261002_allocation_policy.sql` | 版本增量 | allocation_policy / 建表 |
| `backend/migrations/20261002_risk_alert_history.sql` | 版本增量 | risk_watch_event / 建表; risk_watch_mute / 建表 |
| `backend/migrations/20261002_risk_watch.sql` | 版本增量 | risk_watch_rule / 建表; risk_watch_snapshot / 建表 |
| `backend/sql/initsql/allocation_policy.sql` | 通用初始化 | allocation_policy / 建表 |
| `backend/sql/initsql/research_plan.sql` | 通用初始化 | research_plan / 建表; current_timestamp / 更新数据 |
| `backend/sql/initsql/risk_alert_history.sql` | 通用初始化 | risk_watch_event / 建表; risk_watch_mute / 建表 |
| `backend/sql/initsql/risk_watch.sql` | 通用初始化 | risk_watch_rule / 建表; risk_watch_snapshot / 建表 |
| `backend/src/main/resources/db/migration/V2026012801__add_funding_line_type.sql` | 历史版本/辅助 | order_funding_line / 改表; order_funding_line / 更新数据 |
| `sql/V1/DDL.sql` | 历史版本/辅助 | account_groups / 建表; account_pool_rules / 建表; accounts / 建表; advisor_suggestion / 建表; backtest_daily / 建表; backtest_summary / 建表; backtest_trades / 建表; budget_trace / 建表; categories / 建表; daily_balance / 建表; daily_snapshot / 建表; fund_custody_transfer / 建表; indicator_daily / 建表; job_config / 建表; ledger / 建表; market_bar_d / 建表; market_quote_rt / 建表; nav / 建表; orders / 建表; pending_buy_pool / 建表; product_nav_range / 建表; product_strategy_bind / 建表; products / 建表; qdii_premium_rt / 建表; strategy_config / 建表; strategy_state / 建表; trade_fills / 建表; transactions / 建表; account_groups / 删表; account_pool_rules / 删表; accounts / 删表; advisor_suggestion / 删表; backtest_daily / 删表; backtest_summary / 删表; backtest_trades / 删表; budget_trace / 删表; categories / 删表; daily_balance / 删表; daily_snapshot / 删表; fund_custody_transfer / 删表; indicator_daily / 删表; job_config / 删表; ledger / 删表; market_bar_d / 删表; market_quote_rt / 删表; nav / 删表; orders / 删表; pending_buy_pool / 删表; product_nav_range / 删表; product_strategy_bind / 删表; products / 删表; qdii_premium_rt / 删表; strategy_config / 删表; strategy_state / 删表; trade_fills / 删表; transactions / 删表; current_timestamp / 更新数据 |
| `sql/V1/DML.sql` | 历史版本/辅助 | account_groups / 写数据; account_pool_rules / 写数据; accounts / 写数据; categories / 写数据; products / 写数据; strategy_config / 写数据 |
| `sql/initsql/20260928_draft_lifecycle_event.sql` | 通用初始化 | draft_lifecycle_event / 建表 |
| `sql/initsql/DDL.sql` | 通用初始化 | users / 建表; families / 建表; user_family_roles / 建表; product_master / 建表; user_product / 建表; accounts / 建表; broker_fee_config / 建表; fund_sell_fee_tier / 建表; debt_contract / 建表; debt_installment / 建表; ledger_txn / 建表; ledger_posting / 建表; orders / 建表; settlement_confirm / 建表; order_funding_line / 建表; holdings_snapshot / 建表; net_worth_snapshot / 建表; market_bar_daily / 建表; market_quote_realtime / 建表; nav / 建表; indicator_daily / 建表; strategy_config / 建表; product_strategy_bind / 建表; suggestions / 建表; product_tags / 建表; portfolio_targets / 建表; trading_gears / 建表; backtest_plan / 建表; backtest_run / 建表; py_job / 建表; current_timestamp / 更新数据 |
| `sql/initsql/DML.sql` | 通用初始化 | users / 写数据; families / 写数据; user_family_roles / 写数据; product_master / 写数据; users / 更新数据; product_name / 更新数据 |
| `sql/updatesql/20260111/01_init_admin_user.sql` | 版本增量 | users / 写数据; families / 写数据; user_family_roles / 写数据; users / 更新数据 |
| `sql/updatesql/20260112/01_init_product_master.sql` | 版本增量 | product_master / 写数据; product_name / 更新数据 |
| `sql/updatesql/20260112/02_add_product_sort_order.sql` | 版本增量 | product_master / 改表; product_master / 更新数据 |
| `sql/updatesql/20260112/03_add_category_and_reimbursable_fields.sql` | 版本增量 | ledger_txn / 改表 |
| `sql/updatesql/20260113/01_allow_fund_usage_null_for_credit_accounts.sql` | 版本增量 | accounts / 改表 |
| `sql/updatesql/20260113/02_fix_virtual_account_subtype.sql` | 版本增量 | accounts / 写数据; accounts / 删除数据 |
| `sql/updatesql/20260114/01_add_posting_balance_fields.sql` | 版本增量 | ledger_posting / 改表 |
| `sql/updatesql/20260114/02_add_product_note_field.sql` | 版本增量 | product_master / 改表 |
| `sql/updatesql/20260114/03_add_broker_fee_config_table.sql` | 版本增量 | broker_fee_config / 建表; current_timestamp / 更新数据 |
| `sql/updatesql/20260114/04_init_huabao_broker_fee_example.sql` | 版本增量 | broker_fee_config / 写数据; buy_fee_rate / 更新数据; buy_min_fee / 更新数据 |
| `sql/updatesql/20260114/05_add_fund_sell_fee_tier_table.sql` | 版本增量 | fund_sell_fee_tier / 建表; current_timestamp / 更新数据 |
| `sql/updatesql/20260114/06_update_fee_rate_precision.sql` | 版本增量 | product_master / 改表; broker_fee_config / 改表; fund_sell_fee_tier / 改表 |
| `sql/updatesql/20260121/01_add_linked_product_id_to_accounts.sql` | 版本增量 | accounts / 改表 |
| `sql/updatesql/20260121/02_add_shares_to_order_funding_line.sql` | 版本增量 | order_funding_line / 改表 |
| `sql/updatesql/20260122/01_add_mmf_shares_fields.sql` | 版本增量 | accounts / 改表 |
| `sql/updatesql/20260131/01_fix_amount_precision.sql` | 版本增量 | ledger_posting / 改表; ledger_posting / 更新数据 |
| `sql/updatesql/20260205/01_fix_xiaohe_holding.sql` | 版本增量 | accounts / 更新数据 |
| `sql/updatesql/20260604/01_add_boll_kdj_indicator_columns.sql` | 版本增量 | indicator_daily / 改表 |
| `sql/updatesql/20260610/01_create_draft_ledger_entry.sql` | 版本增量 | draft_ledger_entry / 建表; current_timestamp / 更新数据 |
| `sql/updatesql/20260927/01_precheck_draft_ledger_source_duplicates.sql` | 版本增量 |  |
| `sql/updatesql/20260927/02_normalize_blank_draft_ledger_source_ref.sql` | 版本增量 | draft_ledger_entry / 更新数据 |
| `sql/updatesql/20260927/03_add_draft_ledger_source_unique_keys.sql` | 版本增量 | draft_ledger_entry / 改表 |
| `sql/updatesql/20260928/01_create_draft_lifecycle_event.sql` | 版本增量 | draft_lifecycle_event / 建表 |
| `sql/updatesql/20260929/01_settlement_audit_link.sql` | 版本增量 | settlement_confirm / 改表 |

## 逐对象覆盖矩阵

| 对象 | Mapper / 操作 | 通用初始化 | 其他脚本（增量/历史/辅助） | 回滚 / 客户专项 |
| --- | --- | --- | --- | --- |
| `accounts` | `backend/src/main/resources/mapper/AccountMapper.xml`<br>`backend/src/main/resources/mapper/LedgerPostingMapper.xml`<br>`backend/src/main/resources/mapper/LedgerTxnMapper.xml` / 修改,新增,查询 | `sql/initsql/DDL.sql` | `sql/V1/DDL.sql`<br>`sql/V1/DML.sql`<br>`sql/updatesql/20260113/01_allow_fund_usage_null_for_credit_accounts.sql`<br>`sql/updatesql/20260113/02_fix_virtual_account_subtype.sql`<br>`sql/updatesql/20260121/01_add_linked_product_id_to_accounts.sql`<br>`sql/updatesql/20260122/01_add_mmf_shares_fields.sql`<br>`sql/updatesql/20260205/01_fix_xiaohe_holding.sql` | 无独立脚本 |
| `allocation_policy` | `backend/src/main/java/com/timelordtty/dca/mapper/AllocationPolicyMapper.java` / 修改,新增,查询 | `backend/sql/initsql/allocation_policy.sql` | `backend/migrations/20261002_allocation_policy.sql` | 无独立脚本 |
| `broker_fee_config` | `backend/src/main/resources/mapper/BrokerFeeConfigMapper.xml` / 修改,删除,新增,查询 | `sql/initsql/DDL.sql` | `sql/updatesql/20260114/03_add_broker_fee_config_table.sql`<br>`sql/updatesql/20260114/04_init_huabao_broker_fee_example.sql`<br>`sql/updatesql/20260114/06_update_fee_rate_precision.sql` | 无独立脚本 |
| `draft_ledger_entry` | `backend/src/main/resources/mapper/DraftLedgerEntryMapper.xml` / 修改,新增,查询 |  | `sql/updatesql/20260610/01_create_draft_ledger_entry.sql`<br>`sql/updatesql/20260927/02_normalize_blank_draft_ledger_source_ref.sql`<br>`sql/updatesql/20260927/03_add_draft_ledger_source_unique_keys.sql` | 无独立脚本 |
| `draft_lifecycle_event` | `backend/src/main/resources/mapper/DraftLifecycleEventMapper.xml` / 新增,查询 | `sql/initsql/20260928_draft_lifecycle_event.sql` | `sql/updatesql/20260928/01_create_draft_lifecycle_event.sql` | 无独立脚本 |
| `families` | `backend/src/main/resources/mapper/FamilyMapper.xml` / 修改,新增,查询 | `sql/initsql/DDL.sql` | `sql/initsql/DML.sql`<br>`sql/updatesql/20260111/01_init_admin_user.sql` | 无独立脚本 |
| `fund_sell_fee_tier` | `backend/src/main/resources/mapper/FundSellFeeTierMapper.xml` / 修改,删除,新增,查询 | `sql/initsql/DDL.sql` | `sql/updatesql/20260114/05_add_fund_sell_fee_tier_table.sql`<br>`sql/updatesql/20260114/06_update_fee_rate_precision.sql` | 无独立脚本 |
| `holdings_snapshot` | `backend/src/main/resources/mapper/HoldingsSnapshotMapper.xml` / 新增,查询 | `sql/initsql/DDL.sql` |  | 无独立脚本 |
| `indicator_daily` | `backend/src/main/resources/mapper/IndicatorDailyMapper.xml` / 修改,新增,查询 | `sql/initsql/DDL.sql` | `sql/V1/DDL.sql`<br>`sql/updatesql/20260604/01_add_boll_kdj_indicator_columns.sql` | 无独立脚本 |
| `ledger_posting` | `backend/src/main/resources/mapper/LedgerPostingMapper.xml`<br>`backend/src/main/resources/mapper/LedgerTxnMapper.xml` / 修改,删除,新增,查询 | `sql/initsql/DDL.sql` | `sql/updatesql/20260114/01_add_posting_balance_fields.sql`<br>`sql/updatesql/20260131/01_fix_amount_precision.sql` | 无独立脚本 |
| `ledger_txn` | `backend/src/main/resources/mapper/LedgerPostingMapper.xml`<br>`backend/src/main/resources/mapper/LedgerTxnMapper.xml` / 修改,删除,新增,查询 | `sql/initsql/DDL.sql` | `sql/updatesql/20260112/03_add_category_and_reimbursable_fields.sql` | 无独立脚本 |
| `market_bar_daily` | `backend/src/main/resources/mapper/MarketBarDailyMapper.xml` / 修改,新增,查询 | `sql/initsql/DDL.sql` |  | 无独立脚本 |
| `market_quote_realtime` | `backend/src/main/resources/mapper/MarketQuoteRealtimeMapper.xml` / 修改,删除,新增,查询 | `sql/initsql/DDL.sql` |  | 无独立脚本 |
| `nav` | `backend/src/main/resources/mapper/NavMapper.xml` / 修改,新增,查询 | `sql/initsql/DDL.sql` | `sql/V1/DDL.sql` | 无独立脚本 |
| `net_worth_snapshot` | `backend/src/main/resources/mapper/NetWorthSnapshotMapper.xml` / 新增,查询 | `sql/initsql/DDL.sql` |  | 无独立脚本 |
| `order_funding_line` | `backend/src/main/resources/mapper/OrderFundingLineMapper.xml` / 删除,新增,查询 | `sql/initsql/DDL.sql` | `backend/src/main/resources/db/migration/V2026012801__add_funding_line_type.sql`<br>`sql/updatesql/20260121/02_add_shares_to_order_funding_line.sql` | 无独立脚本 |
| `orders` | `backend/src/main/resources/mapper/OrderFundingLineMapper.xml`<br>`backend/src/main/resources/mapper/OrderMapper.xml` / 修改,新增,查询 | `sql/initsql/DDL.sql` | `sql/V1/DDL.sql` | 无独立脚本 |
| `product_master` | `backend/src/main/resources/mapper/ProductMasterMapper.xml` / 修改,新增,查询 | `sql/initsql/DDL.sql` | `sql/initsql/DML.sql`<br>`sql/updatesql/20260112/01_init_product_master.sql`<br>`sql/updatesql/20260112/02_add_product_sort_order.sql`<br>`sql/updatesql/20260114/02_add_product_note_field.sql`<br>`sql/updatesql/20260114/06_update_fee_rate_precision.sql` | 无独立脚本 |
| `research_plan` | `backend/src/main/java/com/timelordtty/dca/mapper/ResearchPlanMapper.java` / 修改,新增,查询 | `backend/sql/initsql/research_plan.sql` | `backend/migrations/20261001_research_plan.sql` | 无独立脚本 |
| `risk_watch_event` | `backend/src/main/java/com/timelordtty/dca/mapper/RiskWatchMapper.java` / 修改,新增,查询 | `backend/sql/initsql/risk_alert_history.sql` | `backend/migrations/20261002_risk_alert_history.sql` | 无独立脚本 |
| `risk_watch_mute` | `backend/src/main/java/com/timelordtty/dca/mapper/RiskWatchMapper.java` / 新增,查询 | `backend/sql/initsql/risk_alert_history.sql` | `backend/migrations/20261002_risk_alert_history.sql` | 无独立脚本 |
| `risk_watch_rule` | `backend/src/main/java/com/timelordtty/dca/mapper/RiskWatchMapper.java` / 修改,新增,查询 | `backend/sql/initsql/risk_watch.sql` | `backend/migrations/20261002_risk_watch.sql` | 无独立脚本 |
| `risk_watch_snapshot` | `backend/src/main/java/com/timelordtty/dca/mapper/RiskWatchMapper.java` / 新增,查询 | `backend/sql/initsql/risk_watch.sql` | `backend/migrations/20261002_risk_watch.sql` | 无独立脚本 |
| `settlement_confirm` | `backend/src/main/resources/mapper/SettlementConfirmMapper.xml` / 新增,查询 | `sql/initsql/DDL.sql` | `sql/updatesql/20260929/01_settlement_audit_link.sql` | 无独立脚本 |
| `user_family_roles` | `backend/src/main/resources/mapper/UserFamilyRoleMapper.xml` / 修改,删除,新增,查询 | `sql/initsql/DDL.sql` | `sql/initsql/DML.sql`<br>`sql/updatesql/20260111/01_init_admin_user.sql` | 无独立脚本 |
| `users` | `backend/src/main/resources/mapper/UserMapper.xml` / 修改,新增,查询 | `sql/initsql/DDL.sql` | `sql/initsql/DML.sql`<br>`sql/updatesql/20260111/01_init_admin_user.sql` | 无独立脚本 |
