# v0.21 数据库迁移离线预检（2026-10-08）

结论：**源码静态覆盖有条件通过；待部署方验证，不能据此认定目标数据库就绪。** 未连接任何数据库、未执行 SQL、未修改财务记录、未部署、未推送。未发现可直接使用且已确认隔离的一次性数据库配置，因此没有尝试寻找密码或访问业务数据源，数据库演练为 NOT_RUN。

审查输入为本任务工作树，起点提交 `15199a49f5d7563959fa60c4eccbf697dbf9ebc5`；未刷新远端。复用 `docs/v020_database_coverage.json` 历史快照（25 Mapper / 164 语句 / 28 对象 / 42 SQL / DB-001），保持原文件不变；其 SHA-256 记录在新证据中。

| 证据层级 | 此次结果 | 含义 |
| --- | --- | --- |
| 脚本存在 | 通过 | 8 组 init/migration 和补充步骤存在 |
| 静态覆盖 | 通过 | 表对象覆盖、双路径结构、计划互斥、顺序、编码检查通过；不是 MySQL 语义或字段全量验证 |
| 专用一次性本地库演练 | 未执行 | 没有创建/删除测试库，也没有以事务回滚冒充 DDL 撤销 |
| 目标环境人工部署确认 | **待部署方验证** | 需要逐环境 schema、脚本执行记录与人工签字 |

运行方式（Python 标准库，无数据库驱动）：

```text
python scripts/migration_preflight.py --output scripts/v021_migration_preflight.json
python scripts/migration_preflight.py --route migration
python scripts/migration_preflight.py --plan scripts/manual-plan.json
python -m unittest discover -s scripts -p test_migration_preflight.py -v
python scripts/audit_mapper_database_coverage.py --repo . --target WORKTREE --output scripts/v021_database_coverage.json
```

`--plan` 是脚本路径字符串数组，只做验证。退出码 0 表示静态检查通过；2 表示缺失对象/步骤、重复路径、顺序或结构等检查不通过；1 表示输入/读取/输出错误。参数错误由 argparse 返回 2。输出限定在 scripts 内。命令没有连接、部署、执行 SQL 或标记人工已部署的功能。

两份完整证据位于 `scripts/v021_database_coverage.json` 和 `scripts/v021_migration_preflight.json`，包含每个 Mapper 文件和 SQL 语句、全部 SQL 文件/对象动作、逐对象初始化与增量覆盖矩阵、索引/约束名及文件行号。审计器来源于 commit-branch-consistency-review 技能，仓库内副本确保离线可复跑，无需本机技能安装。

| 数量闭环 | 数量 |
| --- | ---: |
| XML Mapper / 注解 Mapper | 20 / 5 |
| Mapper 总数 / 已解析 / 未完整解析 | 25 / 25 / 0 |
| Mapper SQL 语句（含公共 SQL 片段） | 164 |
| Mapper 对象 / 有脚本覆盖 / 无覆盖 | 28 / 28 / 0 |
| SQL 脚本 / 脚本涉及对象 | 43 / 66 |
| 仅非初始化建表但通用 init 缺失 | 0（历史为 1） |
| 动态表名 | 0 |
| 索引与约束引用位置 | 278（含重复路径，不是唯一索引数） |

所有历史 SQL 均纳入清单，包括 sql/V1、旧更新、DML；清单不意味着应执行这些脚本。解析器基于静态正则，表/索引位置不等于字段、视图、触发器、存储过程与数据库引擎语义已验证。

人工路径对照（**同一组只选一条**；已部署项应由人工排除，不能盲目重跑）：

| 版本/对象 | 新库 init | 既有库 migration | 前置与重复风险 |
| --- | --- | --- | --- |
| 草稿主表 / DB-001 | sql/initsql/20260610_draft_ledger_entry.sql | sql/updatesql/20260610/01_create_draft_ledger_entry.sql | 基础 owner/family/账本语义；IF NOT EXISTS 不验证已有表结构 |
| v0.8 来源强幂等 | 两条路径均另需 sql/updatesql/20260927/01、02、03 | 同左 | 草稿先建表 → 只读重复预检无重复 → 空来源归一化 → 两个唯一键；02 涉及数据改写必须另行人工授权；03 重跑报重复索引，不能当成功 |
| v0.14 草稿事件 | sql/initsql/20260928_draft_lifecycle_event.sql | sql/updatesql/20260928/01_create_draft_lifecycle_event.sql | 草稿先存在；无 FK；init 有 IF NOT EXISTS，migration 无，重跑报错 |
| v0.14 结算审计字段 | 基础 DDL 后仍需 sql/updatesql/20260929/01_settlement_audit_link.sql | 同一 ALTER | settlement_confirm 已存在；preview_digest / ledger_txn_id；重复 ADD COLUMN 报错 |
| v0.15 回测历史 | 无新 SQL | 无新 SQL | 本地持久化目录权限、备份、损坏记录与配置待人工验证 |
| v0.16 research_plan | backend/sql/initsql/research_plan.sql | backend/migrations/20261001_research_plan.sql | 研究元数据；CREATE TABLE 无重复保护 |
| v0.17 risk_watch_rule/snapshot | backend/sql/initsql/risk_watch.sql | backend/migrations/20261002_risk_watch.sql | owner/family 语义；CREATE TABLE 无重复保护 |
| v0.17 risk_watch_event/mute | backend/sql/initsql/risk_alert_history.sql | backend/migrations/20261002_risk_alert_history.sql | 风险观察先于提醒历史，逻辑依赖无 FK；CREATE TABLE 无重复保护 |
| v0.18 allocation_policy | backend/sql/initsql/allocation_policy.sql | backend/migrations/20261002_allocation_policy.sql | 配置元数据；CREATE TABLE 无重复保护 |
| v0.19 goal_tracking | backend/sql/initsql/goal_tracking.sql | backend/migrations/20261002_goal_tracking.sql | 目标元数据；CREATE TABLE 无重复保护 |
| v0.19 monthly_budget | backend/sql/initsql/monthly_budget.sql | backend/migrations/20261002_monthly_budget.sql | 预算元数据；CREATE TABLE 无重复保护 |

新库先审阅 `sql/initsql/DDL.sql`，再选择补充 init，再补齐 v0.8 和结算字段；初始化 DML 由人工独立审阅，本工具不选入。既有库只审阅缺失的 migration，先核对基础 schema。工具默认返回的是需要人工筛选的完整核对顺序，不是部署执行计划。不能按日期批量执行旧 updatesql，其中有历史财务修复和示例数据。

DB-001 修复：新增 init 文件与 `sql/updatesql/20260610/01_create_draft_ledger_entry.sql:4` **字节完全相同**，没有重写历史 SQL，没有合入来源唯一键；唯一键仍使用独立 v0.8 三步骤。这避免声明不真实的最新 schema，也保留存量重复数据人工处理边界。预检拒绝两条路径同时选入。其余 7 组忽略注释、空白和 IF NOT EXISTS 后结构一致；重复执行特性单列，没有把建表跳过等同于结构一致。

尚未通过的部署明细：

| 编号 / 等级 | 文件位置与证据 | 影响 | 最小处理与复核 |
| --- | --- | --- | --- |
| DB-001 / 已修复 | 历史快照缺失对象；新增 sql/initsql/20260610_draft_ledger_entry.sql:4 | 通用初始化现在具备草稿建表来源 | 字节一致测试通过；部署方择一并核验实际表 |
| DB-002 / 待确认 | sql/initsql/DDL.sql:379 建 settlement_confirm；sql/updatesql/20260929/01_settlement_audit_link.sql:2 加两列 | 主 DDL 单独初始化不具备结算审计字段 | 人工比对 SHOW CREATE TABLE，若缺失才审批 ALTER |
| DB-003 / 警告 | sql/updatesql/20260927/03_add_draft_ledger_source_unique_keys.sql:18 起两个 ALTER | 重复索引、存量重复或空来源会影响强幂等上线 | 先只读重复预检，再授权归一化与加键；逐列核验两个唯一键，不静默删行 |
| DB-004 / 待确认 | backend/migrations/20261002_risk_watch.sql:4 等 CREATE TABLE；当前状态记录 v0.17～v0.19 未部署 | 源码存在不证明目标表存在 | 目标环境提供脱敏表结构、索引和部署记录后人工确认 |
| DB-005 / 待确认 | 全部历史更新清单和基础 DDL 的字段/精度演进 | 静态表对象覆盖无法保证全部字段、约束、数据兼容 | 隔离空库及脱敏升级样本演练，核对 MySQL 8/CHECK/JSON/权限/备份；DDL 隐式提交，恢复需独立方案 |

验证：离线回归 5 项通过，包含 init/migration、DB-001 字节一致、重复路径/顺序/缺步骤、缺失对象与结构漂移、无效编码和 CLI 0/1/2；两条路径静态预检通过。post-task compile hook 的后端 package 与前端 build 通过，git diff --check 通过。hook 使用 MaxFixRounds=0，由当前代理负责修复，避免自动修复扩展到授权范围之外；成功仅 stdout/log，无声音或弹窗。PowerShell 外层启动遇到进程环境兼容错误后改用 cmd 启动同一 hook，未修改 hook。

部署方签署前还需逐对象比对实际列类型、精度、空值、默认值、CHECK/JSON、主键、索引顺序、owner/family 隔离语义，确认只应用未部署脚本。记录环境标识（不要记录密码）、脚本 hash、执行人/审批人/时间、只读核验结果、备份与恢复演练记录；未提供这些证据之前，最终状态始终是 **待部署方验证**。

## 发布硬化复核

2026-10-08 发布硬化补齐跨组前置顺序校验：基础 DDL 先于补充 init/结算 ALTER，草稿及 v0.8 三步骤先于生命周期，风险规则先于提醒历史。默认核对清单同步排序；两条路径及单独重排反例经6项离线测试通过。清单仍仅供人工审核，不执行 SQL，不核验既有 schema。详见 `mydca_v021_release_hardening_20261008.md`。
