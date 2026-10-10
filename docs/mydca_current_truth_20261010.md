# 财富中枢当前事实核对（2026-10-10）

- 任务：`task-aiw-r1-04-mydca-current-state-update-20261010`；执行核对日期：2026-10-11 +08:00。
- 仅更新三份 allowed_paths 文档。未修改 backend/web/sql、历史 Phase 报告或 agent-inbox；不连接数据库/生产服务器，不修改财务记录，不部署，不推送。
- `git ls-remote origin refs/heads/main refs/heads/v2` 实读：main `b9560d35f4458c8b4c7fbd8ecba390b66f216821`，v2 `dd9a4ffda4d00bc19d5cec837e7ebe01f0e56ae5`；本地 HEAD 与 v2 相同。没有依据旧文档重复开发。

## 事实与证据矩阵

| 事实 | 可核对证据 | 验收范围 |
| --- | --- | --- |
| 生产 9 张新表已创建 | [生产报告](mydca_production_deployment_20261010.md)，Git `150c6d88` | draft_lifecycle_event、research_plan、risk_watch_rule/snapshot/event/mute、allocation_policy、goal_tracking、monthly_budget；指定生产环境，不能推广至其他环境 |
| 2 字段与 2 唯一索引已部署 | 同报告的 12 条实际 DDL 与迁移结果 | settlement_confirm.preview_digest/ledger_txn_id 可空，未回填；uk_draft_ledger_user_source/family_source 成功；归一化 UPDATE 跳过 |
| 生产仍为 MySQL 5.7.44-log | 同报告备份与最终验收 | 7 个新 payload 表采用原生 JSON 的 5.7 路径；没有执行数据库版本升级 |
| 最终后端、PC/H5 已部署成功 | 同报告 2026-10-10 10:46 +08:00 最终验收 | JAR SHA-256 `0f54b41430c150004d7c71a2667898d204b513b535c759bd5be25e0f435e39bf`；后端 UP，页面和引用资源哈希校验、授权 GET/未登录401通过 |
| 财务数据未改写 | 同报告 11 张受保护表的只读一致性快照 | 原有字段 SHA-256/行数一致；未测试真实角色写入、交易或正式入账 |
| 恢复演练 NOT_RUN | 同报告备份小节 | 压缩文件读取与定义覆盖验证通过不等于恢复成功；历史视图权限问题未修复 |
| 隔离双引擎演练 NOT_RUN | [隔离演练记录](mydca_mysql57_80_isolated_engine_rehearsal_20261010.md) | 无 Docker 时门禁阻断；不能与生产临时表验证混为一谈 |
| Android 源码 0.21.0/22，本轮 APK 未发布 | [v0.21 工程报告](mydca_v021_release_hardening_20261008.md)、生产报告 | PC/H5 上线不代表 Android 发布或真机验收 |
| 首页目标速览已 Git 交付 | `cbab2c57da332354ce927cddba5205edb0923715`；[目标速览记录](mydca_pc_dashboard_goal_progress_glance_20261010.md)；[组件](../web/pc-app/src/components/GoalProgressGlance.vue) | 手动 GET，最近3个 ACTIVE 目标，作用域/币种隔离，UNKNOWN 不当零；历史测试122通过，专项类型通过，全量类型检查仍有99条历史诊断 |
| 结构文案已 Git 交付 | `cd89f6a62e596f090f47a25715f9a51aefd4671c`；[文案记录](mydca_data_readiness_status_copy_20261010.md)；[后端服务](../backend/src/main/java/com/timelordtty/dca/service/DataReadinessService.java) | 后端 SCHEMA 固定 UNKNOWN；页面不能实时验证，UNKNOWN 不是未部署断言；历史PC测试125通过 |
| KPI/预算文案已 Git 交付 | `dd9a4ffda4d00bc19d5cec837e7ebe01f0e56ae5`；[交付记录](mydca_user_friendly_kpi_budget_copy_20261010.md)；[预算组件](../web/pc-app/src/components/MonthlyBudgetGlance.vue) | 真0/负数保留，缺数据不当0；未读取/无预算/失败/权限分别展示；历史PC测试130通过、专项类型及hook通过 |
| 最新三个 PC 提交是否在生产 | 上述 Git 时间晚于最终部署时间，报告无对应 release/source commit 映射 | **NOT_VERIFIED**，必须对照 release/source commit 和制品哈希；远端 v2 包含不等于生产包含 |

以上测试计数为已有任务报告的历史结果，本次验证另列，不冒称重跑这些代码测试。生产事实来自已批准的历史报告，本次没有远程重验。

## AiCore Work 与 Delivery 核对

只读核对 AiCore 工作区 `reports/delivery/task-aiw-r1-02-friendly-kpi-budget-20261010.json`：current_status=completed，result_commit=dd9a4ffd，final_delivery=completed，tests=passed；build/deploy 在外部汇总字段为 not_required，不能用该字段替代仓库报告的构建记录。通知策略 goal_only，response_verified=false，receipt=not_required，不宣称独立企业微信通知已送达。

只读核对 `reports/goals/aicore-wealthhub-quick-closure-20261010.json`：前三任务 completed，依赖任务 r1-03 提交为 `085950fc933cfdaac0827d4fea19af618d08bbe9`，本任务 eligible/dependencies_satisfied，后续 r1-05 waiting_dependency；Goal pending、notification pending。这是读取时快照，不是本次交付后的状态预测。r1-01/r1-02 的 verified_final_delivery 证明工程交付，不证明生产 release。

AiCore `reports/review/aicore_stale_queue_review_20261010.md` 提醒：done 目录不等于已通过交付门禁，缺 Manifest 的旧任务需保留核验，不自动派生业务任务。本任务的正式外部 Manifest 不在 allowed_paths 内，交由执行器/finalizer生成并核对；此报告和本地 Git 提交提供结果与验证依据，不伪造外部 Manifest 或收据。

## 待办分类与当前入口

分类以 [当前开发状态](CURRENT_DEVELOPMENT_STATE.md) 的“当前真正未完成”为入口：自动工程可做的离线回归/证据核对、真实设备与角色人工验收、另行授权的运行维护、长期设想、本轮搁置、被取代历史计划分别列出。生产结构缺口不再列为首次待部署；恢复、MySQL升级、真实权限/设备体验和最新PC release确认仍未闭环。

旧 Phase/版本报告不改写。其“下一步”只记录当时计划；Android 登录/OCR/通知、草稿与人工结算闭环已经实现，不可从旧报告重新派生重复工作。自动正式入账、交易、结算的人工边界持续有效。

## 本次变更与验证报告

- 更新 CURRENT_DEVELOPMENT_STATE：补充生产事实、后续Git交付及验收边界，修正部署旧断言，重列真实待办。
- 更新 DOCUMENT_INDEX：加入当前核对/生产报告入口，区分实现事实与环境部署事实。
- 新增本报告：记录证据矩阵、AiCore Work只读快照与本次验证。
- `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0`：退出0，Build passed；后端 `mvn -DskipTests package`、shared/PC/H5 build通过。后端单元测试未运行，Android测试/打包不适用本次纯文档范围。日志 `.codex-hooks/logs/latest-build.log`；首次工作树由hook安装依赖，有既有npm配置警告，未改锁文件。成功提示仅stdout/log，hook未修改。
- 三文件UTF-8严格解码通过；20个Markdown相对链接与反引号内仓库文件引用全部存在；`git diff --check`通过，最终暂存再次检查。修改范围仅三份允许文档。
- 本地提交完成后以 `git log -1 --format=%H -- docs/mydca_current_truth_20261010.md` 定位结果；不push，不写外部交付目录。
