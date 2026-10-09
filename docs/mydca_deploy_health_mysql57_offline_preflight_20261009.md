# 部署健康门禁与 MySQL 5.7 离线审查（2026-10-09）

任务 `task-mydca-deploy-health-mysql57-offline-readiness-20261009`；工程 `TimeLordTTY/MyDCA-Board@v2`；工作树起点 `9d93f00085adcdd89c9ce69aff0c7cf13a2356de`。本次修复并验证离线部署模拟；**没有部署，不能认定生产兼容或可上线**。未调用真实 ssh/scp、访问线上服务器、连接任何数据库、执行任何 SQL、修改财务记录或推送。没有生成后续任务。

现场版本 `MySQL 5.7.44-log`、新版本未部署及 **9 张缺表、2 个缺列、2 个缺唯一索引** 均来自 owner-approved 任务描述，本文未再次访问环境核实。其余生产字段、索引和语义仍未知。复用 `docs/mydca_v021_migration_preflight_20261008.md` 与现有 `migration_preflight.py`；未修改历史 SQL。

| 证据状态 | 本次含义 |
| --- | --- |
| STATIC_REVIEW | 20 个固定 SQL 文件文本（8 组双路径 + 4 个补充文件）覆盖检查通过；9/2/2 数量闭环，双路径互斥、结构一致。有限规则扫描不是完整 SQL parser |
| ENGINE_NOT_TESTED | 引擎执行 NOT_RUN。未确认无生产凭据的隔离引擎，不创建库、不执行 DDL/DML；具体命名 CHECK 语法也未实测 |
| PRODUCTION_SCHEMA_BLOCKED | 已知缺口未获升级/迁移授权，其余 schema 未完整核查；`production_ready=false`。离线退出码 0 不能解除阻断 |
| 部署恢复模拟 | 同一 Bash 函数库在隔离目录内执行，假进程/网络，真实文件换包/哈希/恢复通过；真实 Linux 服务恢复仍为 NOT_RUN |

## 修改与恢复守门

`scripts/deploy/standard_deploy.ps1` 保留 `SshTarget=baota-124`、`DeployRoot=/www/wwwroot/wealth-hub`、原 `HealthUrl` 与 `SkipBuild` 接口；远端实现提取至 `remote_deploy.sh`，本地响应解析器为 `health_probe.py`。默认原 Maven 构建仍跳过单元测试，不宣称该命令执行了后端测试。

成功条件为 **本次启动 PID 对应目标 JAR 且存活 → 本机后端 HTTP 200 + JSON 顶层 status=UP → 独立前端 HTTPS 200 → 再次检查后端/PID**。本机 curl 禁用代理、不给重定向、不依赖公开未认证健康地址；响应大小与连接/请求时间有限制，不输出响应正文。HTML/登录页/SPA fallback、缺 status、嵌套 status、DOWN、坏 JSON、重复 JSON key、error JSON、非标准 NaN、401/403/5xx、超时与不可达均失败。HTTPS 去掉 `-k`，证书失败也触发恢复。

上传清单覆盖 JAR、前端所有文件与健康解析器，SHA 校验失败不停止服务。备份包括非空旧 JAR、完整旧前端与首页：拷贝后逐文件比较、生成哈希、再次校验；拒绝旧包/前端符号链接，备份唯一目录避免同秒覆盖。换包前先复制新前端、检查必要工具/Java/日志目录，并持有部署锁。

停止只针对 `/proc` 中 `-jar` 参数解析后等于目标 JAR 的 Java 进程，支持相对 JAR 路径；等待退出，超时不换包。启动子进程显式关闭部署锁 FD 9，并将 stdin 指向 /dev/null，避免旧进程持锁阻断后续部署或继承 SSH 输入。设置恢复标志在第一次停止之前，部分停止、换 JAR、换前端、启动和探针任一步失败均尝试恢复。恢复显式检查每一步返回值，停止残存新服务、校验备份、恢复旧 JAR 和前端、启动并核验旧后端 UP 后才能输出 `deployment_status=rolled_back`（退出 1）。无法确认恢复时输出 **`ROLLBACK_FAILED_MANUAL_RECOVERY_REQUIRED`（退出 2）**，保留上传包、工作目录、备份和仅含退出码的证据，不输出 success。停服前阻断退出 1。原 `artifact_sha256`、`backup_path`、`health_status=200` 成功输出保留。

**实际可靠性边界**：Bash 测试覆盖文件操作与恢复状态机，PID/kill/start/curl/ownership/flock 是明确替身；没有启动真实 Java，没有测试真实 Linux `/proc`、TERM/孤儿进程、端口归属、权限、磁盘满、断电/SIGKILL、SSH 丢线或并发锁竞争。信号 HUP/INT/TERM 注册恢复入口，但不声称所有硬故障可自动恢复；上线前需独立人工恢复方案与隔离服务演练。Actuator UP 也不证明新业务 schema 就绪。

## 精确缺口与非执行清单

下表为**审查定位**，不能直接执行。每组 init/migration 只能由人工按实际环境选一条；已存在对象必须核对实际定义后排除，不能把报错当“已应用”。共 **1+1+2+2+1+1+1=9 表**；已存在的 `draft_ledger_entry` 作为依赖审查，不计入缺表。

| 功能/对象 | 精准增量位置 | 互斥 init 文件 | 依赖与重跑 | 5.7 风险、人工只读信息与执行阻断（均 HIGH） |
| --- | --- | --- | --- | --- |
| 草稿生命周期 / draft_lifecycle_event | `sql/updatesql/20260928/01_create_draft_lifecycle_event.sql:4` | `sql/initsql/20260928_draft_lifecycle_event.sql` | 既有 draft → v0.8 审批链 → lifecycle；逻辑依赖无 FK。migration 重跑报错；init IF NOT EXISTS 不验证已有结构 | DATETIME 默认、继承引擎/字符集/索引与 draft_id/owner 语义需核验；缺结构证据与 DDL 授权 |
| 研究方案 / research_plan | `backend/migrations/20261001_research_plan.sql:3` | `backend/sql/initsql/research_plan.sql` | owner/family 基础语义；两路径 CREATE 重跑报错 | :10 命名 CHECK/JSON_VALID；:8–9 TIMESTAMP(6) 默认与 ON UPDATE；核验 payload 非法值拒绝、隔离键与更新时序。缺 5.7 演练/语义方案 |
| 风险观察 / risk_watch_rule、risk_watch_snapshot | `backend/migrations/20261002_risk_watch.sql:3`、`:11` | `backend/sql/initsql/risk_watch.sql` | rule → snapshot；两路径重跑报错 | :8、:17 命名 CHECK；TIMESTAMP(6)、ascii_bin 主键/规则键。核验 rule_id 关联、owner/family 与快照索引；缺引擎/实际结构证据 |
| 提醒历史/静默 / risk_watch_event、risk_watch_mute | `backend/migrations/20261002_risk_alert_history.sql:2`、`:12` | `backend/sql/initsql/risk_alert_history.sql` | rule/snapshot → event/mute；无 FK；两路径重跑报错 | event :9 命名 CHECK；NULL TIMESTAMP(6) 与 fingerprint/rule_id ascii_bin；mute 没有 payload CHECK，不能凭组名等同兼容。核验去重键、静默时间/NULL/时区与作用域，缺授权/演练 |
| 配置 / allocation_policy | `backend/migrations/20261002_allocation_policy.sql:3` | `backend/sql/initsql/allocation_policy.sql` | owner/family 基础语义；两路径重跑报错 | :9 命名 CHECK、:8 时间精度；核验 payload、所有者索引/默认引擎与字符集，缺语义/引擎证据 |
| 长期目标 / goal_tracking | `backend/migrations/20261002_goal_tracking.sql:3` | `backend/sql/initsql/goal_tracking.sql` | owner/family 基础语义；两路径重跑报错 | :9 `CHECK(JSON_VALID(payload))` 强制性不能保证；核验目标 payload 验证、时间默认与作用域索引，缺兼容修复方案/审批 |
| 月度预算 / monthly_budget | `backend/migrations/20261002_monthly_budget.sql:3` | `backend/sql/initsql/monthly_budget.sql` | owner/family 基础语义；两路径重跑报错 | :9 命名 CHECK；核验预算 payload 验证、时间/月份语义、作用域键；缺引擎测试/生产结构核验 |

| 功能/精准对象 | 文件与行 | 前置、重复与人工核查 | 5.7 风险与阻断（均 HIGH） |
| --- | --- | --- | --- |
| 结算审计 / settlement_confirm.preview_digest | `sql/updatesql/20260929/01_settlement_audit_link.sql:3` | 已存在 settlement_confirm；CHAR(64) NULL，仅展示摘要，非确认凭据；重复 ADD COLUMN 报错 | 普通 ALTER 可能兼容；实际列定义、字符集/默认值未知。与下一列同一 ALTER；DDL 隐式提交不能事务回退；无迁移授权 |
| 结算审计 / settlement_confirm.ledger_txn_id | 同文件 `:4` | VARCHAR(32) NULL；核验真实账本 txn_id 类型/长度与历史无法回溯的边界，不自动回填；重复 ADD 报错 | 可能兼容；实际结构与关联语义未核实，不允许修改正式记录 |
| 来源强幂等 / uk_draft_ledger_user_source | `sql/updatesql/20260927/03_add_draft_ledger_source_unique_keys.sql:16` | `(owner_user_id, source_type, source_ref)`；draft 存在 → 01 预检 → 人工决定 → 02 独立审批 → 03 独立审批 | 既存重复、空/空白来源、排序规则等价、并发写入会阻断；重复索引 1061 不能当成功。核验 NON_UNIQUE=0、三列顺序及实际 NULL/长度 |
| 来源强幂等 / uk_draft_ledger_family_source | 同文件 `:21` | `(owner_family_id, source_type, source_ref)`；相同审批链，owner_family_id 为 NULL 不强制家庭唯一性 | 两个 ALTER 可第一成功、第二失败；必须逐索引核对部分应用状态。核验 5.7 row_format/字节限制、家庭作用域与重复；无数据/DDL 授权 |

v0.8 的审查文件固定为 `01_precheck_draft_ledger_source_duplicates.sql` → 人工处理决策 → `02_normalize_blank_draft_ledger_source_ref.sql` → `03_add_draft_ledger_source_unique_keys.sql`（均在 `sql/updatesql/20260927/`）。**01 在本任务也没有执行**；后续只有独立授权才可由人工只读核查。01 含来源与 ID 明细，后续交付仅提供脱敏计数/摘要，不能写入普通日志。

02 的 **`:17 UPDATE` 同时将 source_ref 改为 NULL，并在 `:19` 改 updated_at=NOW()**，属于真实记录修改，必须独立数据授权；不能因旧注释声称“无需回退”而忽略影响。重复运行只对当前仍空白的记录生效，不能恢复原值/时间。03 的两个唯一键要另获 DDL 审批，执行前重核重复和并发窗口；把重复草稿状态改为 IGNORED 不会移出这些唯一键覆盖范围，不能当解决重复的方法。不得静默删除/合并数据或批量运行历史 updatesql。

## MySQL 5.7 与 8 语义分类

| 分类 | 静态结论与依据 | 必须保持的限制 |
| --- | --- | --- |
| 已知 8 特性/语法阻断 | 仓库多处 `CONSTRAINT name CHECK(...)`；5.7 官方 CREATE TABLE 文档只定义有限 `CHECK(expr)`，不含命名 CHECK；8.0.16+ 增加命名/强制 CHECK | 具体原文能否解析没有引擎证据，NOT_RUN；不能说 5.7 一定能原样建表。需人工修复方案或升级决策，不能擅改历史 SQL |
| 必不等价的强制语义 | 5.7 即使接受有限 CHECK，所有引擎都忽略约束；LONGTEXT + CHECK(JSON_VALID(payload)) 不保证合法 JSON，draft 状态 CHECK 同样不能依赖 | 8.0.16+ 默认执行 CHECK，早于该版本的 MySQL 8 也不能笼统宣称强制。删除 CHECK 以通过语法不会保留校验语义，需审批校验方案 |
| 可能兼容 | JSON 类型自 5.7.8 支持并验证输入，JSON_VALID 可用；TIMESTAMP(6)/DATETIME/CURRENT_TIMESTAMP 精度及普通复合索引是 5.7 具备的能力 | 原生 JSON 与 LONGTEXT 不等价；5.7 JSON 重复 key 规范化与 8 不同。函数/类型支持不能证明所有应用查询、排序或既存数据兼容；需逐语句/数据验证 |
| 未判断 | 实际默认引擎、字符集/排序规则、sql_mode、explicit_defaults_for_timestamp、时区、列演进、权限、锁表/磁盘及完整业务语义 | TIMESTAMP 范围和 NULL/default 行为必须核查；其余生产对象未全量验证。规则未命中也不代表兼容 |
| 恢复保障阻断 | 5.7 CREATE/ALTER 等 DDL 隐式提交，不能事务 ROLLBACK 恢复 schema | 独立备份/恢复演练及逐步核对必需；应用 JAR/前端回滚不能恢复数据库 |

普通唯一索引候选 `source_type VARCHAR(32)` + `source_ref VARCHAR(128)` 在 utf8mb4 时字符串部分最大 640 字节，加一个 BIGINT 为约 648 字节；这只是源码候选估算，既有字段/排序规则未验证，不能代替目标索引长度核查。其他继承字符集的旧索引也需单独检查。5.7 的 767/3072 字节限制受 innodb_large_prefix、COMPACT/REDUNDANT 与 DYNAMIC/COMPRESSED、page_size 等影响。

官方依据（查阅参考文档没有访问生产系统；使用 Oracle 固定 5.7 文档，避免 dev.mysql.com 旧版链接重定向至新版）：[5.7 CREATE TABLE/CHECK/索引](https://docs.oracle.com/cd/E17952_01/mysql-5.7-en/create-table.html)、[8.0.16+ CHECK](https://dev.mysql.com/doc/refman/8.0/en/create-table-check-constraints.html)、[5.7 JSON](https://docs.oracle.com/cd/E17952_01/mysql-5.7-en/json.html)、[5.7 时间精度](https://docs.oracle.com/cd/E17952_01/mysql-5.7-en/fractional-seconds.html)、[5.7 InnoDB 限制](https://docs.oracle.com/cd/E17952_01/mysql-5.7-en/innodb-limits.html)、[5.7 DDL 隐式提交](https://docs.oracle.com/cd/E17952_01/mysql-5.7-en/implicit-commit.html)。

## 可重复离线验证与证据

| 实际运行 | 结果与有限范围 |
| --- | --- |
| `python -m unittest discover -s scripts/deploy -p test_deploy_offline.py -v` | 首轮 5 测试方法通过；21 个 Bash 场景（1 成功、12 停服后失败并确认恢复、5 停服前阻断、3 不可恢复）及严格探针反例；耗时 306.413 秒。最终恢复反例另验证上传包和失败证据确实保留；只有模拟进程/网络 |
| 同一 unittest 命令追加 `-k test_start_child` | 第 6 个方法通过；用假 nohup Bash 子进程实际执行生产 start_service，确认不继承 FD 9/SSH stdin；未调用 Java/native nohup |
| 同一 unittest 命令追加 `-k test_cleanup_failure` | 第 7 个方法通过；第 22 个场景验证成功信号只能在清理结束后输出，清理失败也恢复旧服务并验证 UP。备份包含经哈希校验的健康解析器，工作目录可重建，部分清理不影响恢复判定 |
| `powershell -ExecutionPolicy Bypass -File scripts/deploy/test_standard_deploy_offline.ps1` | PASS；假 scp 失败不调用假 ssh；假 ssh 流入口、参数引号/元字符、JAR/前端/解析器完整 SHA。长工作树导致 Windows tar 路径限制，此夹具使用 Python 本地压缩；Bash 模拟使用真实 tar |
| `python -m unittest discover -s scripts -p test_mysql57_compat_preflight.py -v` | 8 项通过：9/2/2、双路径、CHECK 分类、语义/行号、缺对象/漂移拒绝、UPDATE 独立审批、禁止网络/子进程、CLI 帮助/输出范围/不覆盖/拒绝执行参数 |
| `python -m unittest discover -s scripts -p test_migration_preflight.py -v` | 原有 6 项通过；未改变历史预检实现 |
| `python scripts/migration_preflight.py --route migration` | 退出 0，static_coverage_pass=true；disposable_database_rehearsal=NOT_RUN，人工部署仍待验证 |
| `python scripts/mysql57_compat_preflight.py --output scripts/deploy/mysql57_static_review_20261009.json` | 退出 0，只新建脱敏本地 JSON，9/2/2、各文件 hash/分类/行号和审批链完整；production_ready=false |
| `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0` | 后端 Maven package、前端 npm build 通过（hook 跳过后端测试）；成功只 stdout/log，无 beep/toast。日志为本地 `.codex-hooks/logs/latest-build.log` |
| `git diff --check` | 通过；提交前复核仅 allowed_paths 的变更 |

新 CLI 的 `--help`/`--route`/`--output` 只做本地文本审查；输出必须是 `scripts/deploy/` 内新 `.json`，解析最终路径限制越界，不覆盖已有文件，也没有数据库连接、SQL 执行或远端执行入口。报告位于 `scripts/deploy/mysql57_static_review_20261009.json`，20 个输入文件各记录 hash；报告包含依赖的 draft 建表文本，但不计入已知缺表。不得把该报告或旧预检的核对顺序拼成上线批处理。

## 后续受控人工审查的前置条件

1. 另行批准只读环境核验，提供脱敏 schema/索引/环境设置与实际运行 JAR hash；逐对象核对以上 13 个缺口及其余对象，而非仅检查表名存在。
2. 人工决定升级到受支持环境还是设计 5.7 语义补偿；具体命名 CHECK 语法、非法 JSON 拒绝、时间/排序/索引需在已确认隔离、无生产凭据的环境单独授权演练。本任务没有该权限。
3. 分开审批 v0.8 只读预检、重复/空来源处理、normalization 和索引 DDL；不自动修改真实记录。明确 DDL 部分应用、备份/恢复与并发窗口方案。
4. 用隔离 Linux 服务验证 `/proc` 匹配、真实 TERM/启动、权限/日志、固定本机健康响应、HTTPS 证书和失败恢复；健康探针可访问性/授权问题通过独立变更审查解决，不擅改 application/Nginx。
5. 以上证据齐全后才可另行人工批准受控部署；本次本地提交不授权线上操作，也不代表已部署或生产迁移完成。

## Delivery Manifest 与外部收据边界

本地 manifest 在 `scripts/deploy/offline_delivery_manifest_20261009.json`，结果版本由包含该文件的本地 Git commit 精确定位，最终 SHA 随工程交付返回。工程内容与离线验证可交付；AiCore 独立 Delivery Manifest 发布与 Hermes/WeCom **verified receipt 尚未核验**。当前没有可调用的 AiCore/Hermes 投递工具，allowed_paths 也不允许写入外部任务/交付目录，因此没有冒造收据或修改 agent-inbox。后续由独立交付链关联 result commit、发布 manifest 并取得真实收据；不把“通知已准备”当 verified receipt。
