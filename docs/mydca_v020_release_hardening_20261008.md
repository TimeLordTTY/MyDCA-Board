# v0.20 目标与现金流情景发布硬化

日期：2026-10-08。任务：`task-mydca-v020-release-hardening-20261008`。仅本地提交、不推送、不部署、不连接业务数据库；通知策略为 goal_only，由 AiCore/Hermes 汇总。

## 交付范围与证据

上游四个提交均为当前 HEAD 的祖先，相关代码及回归在本地树：

| 能力 | 本地提交 | 契约与实现 |
| --- | --- | --- |
| 单目标/月度预算联动 | f7d7a9592fb51a329268f3d010d0cd9ba03efb9d | backend/GOAL_CASHFLOW_FORECAST_API.md、GoalForecastService |
| 多目标共享结余与情景对比 | ec7890ee09d675208fa3c7496422b77eec699a29 | backend/MULTI_GOAL_SCENARIO_API.md、MultiGoalScenarioService |
| Android 零收益查看 | 2327885b328b077ddc682daa3b2b2c0b7a0cb071 | android-app/GOAL_BUDGET_VIEW.md、GoalForecastScreen |
| PC 情景工作台 | 3bd93154310f060711df044640f19f4cbdc462e4 | GoalForecastWorkbench.vue、goalForecastModel.ts |

本次修复 PC 年份上界：用户输入的期间超过9998-12时整体拒绝，原实现过滤超界月份可能静默缩短期间。回归包含9998年合法12个月与跨上界60个月。Android 新页面形成版本增量，统一 versionName 0.20.0 / versionCode 21 和 GitHub Actions 预期命名。README、当前状态与文档索引由本任务统一更新。

这里证明的是源码与本地测试交付。未读取或核验外部 AiCore Delivery Manifest、上游通知收据，不声称上游 Delivery 状态或 Goal 企业微信通知已完成；这些证据须由调度系统关联本报告及提交后汇总验收。

## 回归与安全边界

| 要求 | 验证证据及结果 |
| --- | --- |
| 单目标联动、收入/支出/预留、赤字与完成上限 | GoalForecastServiceTest；当月扣已发生实际结余，避免重复增加资产 |
| 多目标共享、不重复计数 | MultiGoalScenarioServiceTest；总额度与单一共享上限比较，超限所有目标进度未知，额外储蓄只计一次 |
| 情景对比 | 同请求复用授权目标/预算/进度/实际证据，字段变化单列；不承诺跨来源原子实时快照 |
| 0%基线与用户收益 | 后端 explicitRateProducesSeparateScenario / changedHorizonAndRateAreReported；缺省仅基线，显式年化另列数学情景。PC/Android 当前仅零收益，未提供收益编辑入口 |
| UNKNOWN/PARTIAL | 后端未知起始/缺预算/币种不符/持仓目标/未覆盖月份；PC 与 Android 显示已知部分，未知不当零，不发明达成日期 |
| owner/family 权限 | GoalTrackingServiceTest、MonthlyBudgetServiceTest 验证不同 user/current-family、家庭管理员；forecast 拒绝未授权目标且不继续读取预算，多目标拒绝混合作用域 |
| 历史目标与预算 | 暂停/归档状态沿用只读模拟，无 DTO/元数据结构变更；既有生命周期、预算实际对比及月份边界回归通过 |
| 中文、加载/错误/重试 | PC role=status/alert、标签与键盘可聚焦表单；Android 复用 polite liveRegion，失败清除旧数据并人工重试；MockWebServer 验证401/403/503与分页/多预算歧义 |
| 离线 | 页面不持久化情景、不自动执行，不联网时显示错误；测试验证读取失败和人工恢复，实际断网与设备体验待验收 |

调用链为两个只读 POST Controller → forecast/compare（readOnly事务）→既有授权 detail/progress/comparison →只读财务证据及纯数学计算。无金融 writer 注入或调用，不创建交易、转账、定投、订单、结算、正式账本，不写资金余额、持仓、占用或目标/预算元数据。客户端仅手动发送情景参数；Android不声明现金流覆盖，不推断自由资金。本次无新增配置键、依赖、Mapper或migration；仍依赖v0.19目标/预算表及既有数据来源，由部署方人工确认schema。

## 交付一致性审查

范围为 v0.19 收口 `538d39524b54acdc594c590b8541ab28f691f5a4` 到上游 HEAD `3bd93154310f060711df044640f19f4cbdc462e4` 的31个文件；清单见 `v020_review_evidence.json`。未fetch。接口、DTO、shared API导出、PC入口、Android Retrofit/仓库/页面与测试相互对应。两份后端契约和Android专项文档已存在，发布版本、CI命名及三份主文档本次补齐。未进行整个仓库的全部业务语义审查。

全量数据库结构枚举及逐对象矩阵见 `v020_database_coverage.json`，覆盖目标树而非仅diff。枚举结果：XML Mapper 20 + 注解 Mapper 5 = 25，完整解析25、未解析0；SQL语句164；引用对象28 = 有脚本覆盖28 + 无覆盖0；SQL脚本42、涉及对象66；新增引用对象0、动态表名0。结构覆盖不证明实际数据库部署或所有SQL执行语义。

审查结论：v0.20工程有条件通过，外部交付与人工验收待确认。数据库枚举发现1项既有初始化缺口，保留原有部署路径，不扩大本次修改范围：

| 编号 | 等级 | 文件与位置、证据 | 影响与建议 | 复核 |
| --- | --- | --- | --- | --- |
| DB-001 | WARNING（既有） | backend/src/main/resources/mapper/DraftLedgerEntryMapper.xml:36 引用 draft_ledger_entry；sql/updatesql/20260610/01_create_draft_ledger_entry.sql:4 有建表，通用init无对应建表 | 只执行通用init的新环境可能缺草稿表；部署方按既有增量链人工建表，后续独立任务补齐通用初始化覆盖。sql/** 不在本任务允许编辑范围 | 逐对象矩阵及初始化/增量路径核对，未执行SQL |
| DELIVERY-001 | NEEDS_CONFIRMATION | 本地祖先提交与测试可验证，外部Delivery Manifest/收据未核验 | AiCore关联四个上游task、source commit、Delivery与goal_only收据后验收Goal | 调度系统真实交付记录 |

## 实际验证

- `mvn test`：255项，失败0、错误0、跳过0。
- 前端首次运行缺依赖/shared dist；`npm ci` 和 `npm run build:shared` 后，`node --test pc-app/tests/*.test.mjs`：33项通过。
- `npx tsc -p pc-app/tests/tsconfig.goalForecast.json --noEmit` 和 `npx vue-tsc --noEmit -p pc-app/tests/tsconfig.goalForecast.json`：通过。
- Android 首次失败为本地SDK路径未设置；仅进程设置 ANDROID_HOME 指向本机SDK后重跑，无提交本机配置。
- Android `testDebugUnitTest assembleDebug lintDebug`：BUILD SUCCESSFUL；45个测试类、296项测试、失败0、错误0；lint 0 errors / 2 warnings（既有问题）。仅本地debug APK，无CI制品声明。
- post-task compile hook：Build passed，后端package与完整web build通过。由 `cmd /c powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1` 执行，成功模式保持stdout/log，无声音、Toast或Popup。文档回填后再次执行。
- `git diff --check`：通过；提交前再次核验。

## 人工验收与制品

1. 部署方核验目标环境schema与历史目标/预算，不由本任务连接数据库或部署migration。
2. 真实登录的不同owner/current-family/管理员权限、月度实际数据及统计日正确性；mock不证明目标环境上线。
3. PC键盘、读屏、长表滚动、预算分页选择和真实断网后手动重试；Android真机TalkBack、目标入口、滚动、返回、会话过期和真实断网恢复。
4. 推送后真实GitHub Actions成功，再填source commit、Run ID、Artifact ID与APK SHA-256。预期artifact `mydca-android-v0.20.0-<full-sha>`、APK `MyDCA-Board-v0.20.0-<short-sha>.apk`，均不是现有CI证据。
5. AiCore/Hermes核验上游Delivery并按goal_only汇总通知，本执行器未发送企业微信消息。
