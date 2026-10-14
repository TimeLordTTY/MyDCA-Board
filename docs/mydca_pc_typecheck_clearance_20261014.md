# PC 类型诊断清零：2026-10-14

任务：task-mydca-pc-typecheck-clearance-20261014。工程离线验收 PASS；候选集成/Finalizer/Hermes 验收由控制器后续执行。本任务未推送、未发布、未连接生产或数据库、未改真实财务记录。

输入 HEAD / 控制器基线：`9008ec9e646b3804fd7d6a6fd6e0651be17338e1`。唯一结果提交的 SHA 由 `git log -1 --format=%H -- docs/mydca_pc_typecheck_clearance_20261014.md` 独立取得，避免在提交内写入无法自引用的 SHA；最终回复提供完整 SHA。

## 基线与修改

唯一类型基线证据为控制器 `runs/20261014-031314-16688-0/controller-baseline/baseline.json` 及其 `types-baseline.log`；完整绝对路径见本任务命令原始 JSONL。基线实际退出码2、44条诊断，日志 SHA-256 `da3078b9267dc499733e483a4e702346229d891167fa4e019245010924adce32`。比较脚本验证报告与日志诊断多重集合一致，并在结束再次校验两份证据哈希。未修改基线树、未重跑基线、未替换当前源码。

44条均为 TS6133 / TS6196 未使用声明。六个产品源码文件仅移除无引用声明、导入和模板参数；同时删除由此孤立的三个父账户 computed、Dashboard 的两个数学辅助函数及 isBuyType。保留仍有调用的 findAccountById、calculateParentBalances、级联选择事件、分配校验、API DTO、权限与人工确认链，无新增 any、忽略注解或类型配置修改。

三份 Dashboard 测试原有历史源码相等断言将未调用辅助函数也纳入比较，随清理失效。改为检查当前手动入口、安全契约和待办只读导航，保留 dashboardHome 的 BUY/SUBSCRIPTION/SELL/REDEMPTION 运行时 preview→owner→freshToken 确认、阻断与取消测试，不用历史源码作为本任务类型基线。

## 实际验证与原始证据

所有 Node 命令固定本次环境 `v24.19.0`；仓库 web/package.json 无 engines 或独立版本入口，未改依赖、锁文件或严格设置。

| 命令 | 最终退出码 / 结果 | 证据 |
| --- | --- | --- |
| `npm.cmd ci --ignore-scripts --no-audit --no-fund`（web） | 0，156 packages | 控制器原始输出，chunk 4b09fd |
| `npm.cmd --prefix web/shared run build` | 0 | chunk dbc4af；完整 hook 也重建 shared |
| `node web/node_modules/vue-tsc/bin/vue-tsc.js --noEmit -p web/pc-app/tsconfig.json` | 0，诊断0 | 比较脚本完整输出 / tests/typecheckClearance.result.json |
| `node web/pc-app/tests/typecheckClearance.mjs <controller-baseline/baseline.json>` | 0，全量与21局部配置通过，新增0、移除44 | tests/typecheckClearance.result.json 含完整原诊断及最终空输出 |
| `node --test --test-reporter=dot web/pc-app/tests/*.test.mjs` | 0，273个用例通过 | chunks 6d2dfa + ad6561；控制器保存完整原始输出 |
| `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1` | 0，完整 backend package + shared/PC/H5 build | tests/typecheckClearance.build.raw.log；最终 hook 原始 JSONL |
| 隔离 Edge fixture（下述精确命令） | 0，PASS | tests/typecheckClearance.browser.raw.log，包含完整 fixture 回显和结果 |
| `git diff --check` | 0 | 控制器原始输出 |

最初工作树缺依赖导致 shared/types 无法启动，安装锁文件依赖后通过；清理产生 Dashboard isBuyType 孤立声明及三处旧测试断言失败，已修复并执行最终全套回归。上述为最终结果，不掩盖初始失败。

Edge 会话先执行：`npx.cmd --yes --package @playwright/cli@0.1.22 playwright-cli -s=typecheck-clearance-final-20261014 open about:blank --browser msedge`。

随后在 web/pc-app 原生执行：`npx.cmd --yes --package @playwright/cli@0.1.22 playwright-cli -s=typecheck-clearance-final-20261014 run-code --filename tests/typecheckClearance.browser.js`。仅使用 Vite preview 的本地静态构建（127.0.0.1:3110），未启动业务后端或开发代理；业务 HTTP 全部 fulfill/abort，外部 origin abort。使用合成 token，无真实登录凭据。测试覆盖未知分类、真实0、列表失败/恢复/空态、1440/1100/760宽度、账户级联、人工核对前无写、金额12.34、mock提交未知后禁止重复提交。

最终浏览器结果：`{"result":"PASS","boundary":"ALL_BUSINESS_REQUESTS_INTERCEPTED_NO_SERVER_OR_PRODUCTION_CONNECTION","widths":[1440,1100,760],"pageerrors":[],"unexpectedRequests":[],"interceptedRequests":29,"financialWrites":0,"fixtureWrites":1}`。两个 console error 来自预期的 mock HTTP 500（列表失败及提交结果未知），不是 pageerror。fixtureWrites=1 是被拦截的合成请求，不代表真实财务写。

SHA-256：fixture `f7f08c5b3a8b418303129f4e415bb65634f5bf51601e2c226a9b24a545412223`；浏览器原始输出 `42034f5ad6d4f93425013c89762ad392e7cab191120b5d3de387a187917fc44c`；构建原始输出 `8cf1ebc1e748980b961f301e3dd9660799ce278e20ebd8cf9fa1379215a3e7ef`。日志按原始字节保存，可独立 Get-FileHash 复核。

完整 hook 使用现有脚本、保持提示配置；不修改脚本，不杀进程，清理由控制器管理。backend 的 `-DskipTests package` 是构建，后端单元测试、数据库集成、真实角色/设备、真实财务 E2E、生产迁移、部署均 NOT_RUN。mock通过不解除原发布报告的 owner/真实权限/设计及上线门禁。

## 44条完整诊断覆盖矩阵

下表文件行列均来自控制器固定日志；每条最终诊断数量0，修复依据为未使用声明（删除或取消未使用绑定）。新生成的孤立声明也已清理。

| # | 原文件与行列 / 完整诊断 | 最终 |
| --- | --- | --- |
| 1 | web/pc-app/src/components/NewOrderModal.vue(218,38): error TS6133: 'acc' is declared but its value is never read. | 0 |
| 2 | web/pc-app/src/components/QuickEntryModal.vue(61,112): error TS6133: 'getCategoryDisplayName' is declared but its value is never read. | 0 |
| 3 | web/pc-app/src/components/QuickEntryModal.vue(62,24): error TS6196: 'Category' is declared but never used. | 0 |
| 4 | web/pc-app/src/components/RefundModal.vue(141,11): error TS6133: 'txnAny' is declared but its value is never read. | 0 |
| 5 | web/pc-app/src/components/ReimburseModal.vue(106,9): error TS6133: 'reimburseCategory' is declared but its value is never read. | 0 |
| 6 | web/pc-app/src/components/ReimburseModal.vue(206,11): error TS6133: 'categoryId' is declared but its value is never read. | 0 |
| 7 | web/pc-app/src/components/UnifiedEntryModal.vue(84,39): error TS6133: 'node' is declared but its value is never read. | 0 |
| 8 | web/pc-app/src/components/UnifiedEntryModal.vue(139,43): error TS6133: 'node' is declared but its value is never read. | 0 |
| 9 | web/pc-app/src/components/UnifiedEntryModal.vue(201,39): error TS6133: 'node' is declared but its value is never read. | 0 |
| 10 | web/pc-app/src/components/UnifiedEntryModal.vue(274,41): error TS6133: 'node' is declared but its value is never read. | 0 |
| 11 | web/pc-app/src/components/UnifiedEntryModal.vue(296,41): error TS6133: 'node' is declared but its value is never read. | 0 |
| 12 | web/pc-app/src/components/UnifiedEntryModal.vue(480,39): error TS6133: 'node' is declared but its value is never read. | 0 |
| 13 | web/pc-app/src/components/UnifiedEntryModal.vue(592,39): error TS6133: 'node' is declared but its value is never read. | 0 |
| 14 | web/pc-app/src/components/UnifiedEntryModal.vue(778,39): error TS6133: 'node' is declared but its value is never read. | 0 |
| 15 | web/pc-app/src/components/UnifiedEntryModal.vue(920,39): error TS6133: 'node' is declared but its value is never read. | 0 |
| 16 | web/pc-app/src/components/UnifiedEntryModal.vue(1063,39): error TS6133: 'node' is declared but its value is never read. | 0 |
| 17 | web/pc-app/src/components/UnifiedEntryModal.vue(1158,39): error TS6133: 'node' is declared but its value is never read. | 0 |
| 18 | web/pc-app/src/components/UnifiedEntryModal.vue(1184,39): error TS6133: 'node' is declared but its value is never read. | 0 |
| 19 | web/pc-app/src/components/UnifiedEntryModal.vue(1227,49): error TS6133: 'getFundUsageLabel' is declared but its value is never read. | 0 |
| 20 | web/pc-app/src/components/UnifiedEntryModal.vue(1411,7): error TS6133: 'parentAccounts' is declared but its value is never read. | 0 |
| 21 | web/pc-app/src/components/UnifiedEntryModal.vue(1450,7): error TS6133: 'availableChildAccounts' is declared but its value is never read. | 0 |
| 22 | web/pc-app/src/components/UnifiedEntryModal.vue(1466,7): error TS6133: 'availableFromChildAccounts' is declared but its value is never read. | 0 |
| 23 | web/pc-app/src/components/UnifiedEntryModal.vue(1482,7): error TS6133: 'availableToChildAccounts' is declared but its value is never read. | 0 |
| 24 | web/pc-app/src/components/UnifiedEntryModal.vue(1757,7): error TS6133: 'expenseAllocationError' is declared but its value is never read. | 0 |
| 25 | web/pc-app/src/components/UnifiedEntryModal.vue(1887,7): error TS6133: 'selectedParentAccountBalance' is declared but its value is never read. | 0 |
| 26 | web/pc-app/src/components/UnifiedEntryModal.vue(1888,7): error TS6133: 'selectedParentAccountCredit' is declared but its value is never read. | 0 |
| 27 | web/pc-app/src/components/UnifiedEntryModal.vue(1891,7): error TS6133: 'selectedChildAccountBalance' is declared but its value is never read. | 0 |
| 28 | web/pc-app/src/components/UnifiedEntryModal.vue(1899,7): error TS6133: 'isSelectedChildCreditAccount' is declared but its value is never read. | 0 |
| 29 | web/pc-app/src/components/UnifiedEntryModal.vue(1913,7): error TS6133: 'selectedFromParentAccountBalance' is declared but its value is never read. | 0 |
| 30 | web/pc-app/src/components/UnifiedEntryModal.vue(1914,7): error TS6133: 'selectedFromParentAccountCredit' is declared but its value is never read. | 0 |
| 31 | web/pc-app/src/components/UnifiedEntryModal.vue(1917,7): error TS6133: 'selectedFromChildAccountBalance' is declared but its value is never read. | 0 |
| 32 | web/pc-app/src/components/UnifiedEntryModal.vue(1925,7): error TS6133: 'isSelectedFromChildCreditAccount' is declared but its value is never read. | 0 |
| 33 | web/pc-app/src/components/UnifiedEntryModal.vue(1939,7): error TS6133: 'selectedToParentAccountBalance' is declared but its value is never read. | 0 |
| 34 | web/pc-app/src/components/UnifiedEntryModal.vue(1940,7): error TS6133: 'selectedToParentAccountCredit' is declared but its value is never read. | 0 |
| 35 | web/pc-app/src/components/UnifiedEntryModal.vue(1943,7): error TS6133: 'selectedToChildAccountBalance' is declared but its value is never read. | 0 |
| 36 | web/pc-app/src/components/UnifiedEntryModal.vue(1951,7): error TS6133: 'isSelectedToChildCreditAccount' is declared but its value is never read. | 0 |
| 37 | web/pc-app/src/components/UnifiedEntryModal.vue(2630,10): error TS6133: 'handleParentAccountChange' is declared but its value is never read. | 0 |
| 38 | web/pc-app/src/components/UnifiedEntryModal.vue(2634,10): error TS6133: 'handleRepaymentAccountChange' is declared but its value is never read. | 0 |
| 39 | web/pc-app/src/components/UnifiedEntryModal.vue(2769,10): error TS6133: 'handleFromParentAccountChange' is declared but its value is never read. | 0 |
| 40 | web/pc-app/src/components/UnifiedEntryModal.vue(2774,10): error TS6133: 'handleToParentAccountChange' is declared but its value is never read. | 0 |
| 41 | web/pc-app/src/components/UnifiedEntryModal.vue(2779,10): error TS6133: 'handleBuyParentAccountChange' is declared but its value is never read. | 0 |
| 42 | web/pc-app/src/components/UnifiedEntryModal.vue(2785,10): error TS6133: 'handleBuyAccountChange' is declared but its value is never read. | 0 |
| 43 | web/pc-app/src/components/UnifiedEntryModal.vue(2800,10): error TS6133: 'handleSellParentAccountChange' is declared but its value is never read. | 0 |
| 44 | web/pc-app/src/views/Dashboard.vue(834,10): error TS6133: 'calculateSettlementFromForm' is declared but its value is never read. | 0 |
