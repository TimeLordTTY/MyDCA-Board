# MySQL 5.7 / 8 一次性隔离引擎演练与上线阻断报告

任务：`task-mydca-mysql57-80-isolated-engine-rehearsal-20261010`。日期：2026-10-10（Asia/Hong_Kong）。仓库：`TimeLordTTY/MyDCA-Board`，目标版本 v2，工作树起点 `3e76587fa3b6f639e9a0f4d004d64cc02495faa2`。工作树由调度器以 detached HEAD 隔离创建；结果由包含本文的本地提交定位，不移动其他工作树的 v2，不推送。

**当前结论仍为 `ENGINE_NOT_TESTED / PRODUCTION_SCHEMA_BLOCKED`，`production_ready=false`。** 本机 PATH 和常见安装目录未发现 Docker/Podman；没有获得经验证的一次性空白 MySQL 引擎。显式执行入口在本机引擎前置门禁以 `DOCKER_NOT_AVAILABLE` 退出，未拉取镜像、创建容器或执行 SQL。不能用离线替身测试的成功补充引擎证据。

本次交付可重复工具与离线验证；真实 5.7/8 差异仍未知。没有连接任何已有数据库、访问生产地址、读取 datasource/.env/生产凭据、执行部署、正式入账、交易或修改真实记录，也没有修改 agent-inbox 或创建任务/Goal。已知 9 表、2 列、2 唯一索引缺口引用已批准任务及上游静态报告，本次没有重新查询生产环境。

## 本机门禁与可重复执行

入口：`scripts/deploy/mysql_isolated_rehearsal.py`，仅依赖 Python 标准库。默认 dry-run 只读取固定 11 个 SQL 文件；`--help` 不运行子进程、不连接网络/数据库。每个输入的原始字节 SHA-256 固定在代码中，记录在 JSON 中，文件/换行变化均安全阻断，需人工复核后更新工具固定值。没有 SQL 文件、host、database、password、dsn 或 image 参数。

```powershell
# 默认只读，输出 ENGINE_NOT_TESTED，不启动 Docker
python scripts/deploy/mysql_isolated_rehearsal.py
python scripts/deploy/mysql_isolated_rehearsal.py --help

# 仅在已具备可信本机 Docker 时，在批准有效期内显式执行
# 输出文件必须是 scripts/deploy 内尚不存在的新 JSON；不能覆盖本次证据
python scripts/deploy/mysql_isolated_rehearsal.py --run-isolated --output scripts/deploy/mysql_isolated_engine_next_run.json

# 离线安全测试；全部 Docker/SQL 响应为明确的替身
python -m unittest discover -s scripts/deploy -p test_mysql_isolated_rehearsal.py -v
```

执行门禁只接受默认 context 的 `unix:///var/run/docker.sock` 或本机 `npipe:////./pipe/docker_engine`，以及 Docker Desktop `desktop-linux` 的本机 `npipe:////./pipe/dockerDesktopLinuxEngine`；Linux 容器引擎才可执行。拒绝非空 DOCKER_HOST/DOCKER_CONTEXT/DOCKER_TLS_VERIFY/DOCKER_CERT_PATH/DOCKER_CONFIG，拒绝 ssh/tcp、远端 named pipe、未知 context/endpoint。后续命令固定 `--context`。这些校验以可信本机 Docker 安装及本机 socket 为前提，不宣称能识别被恶意替换的 Docker 程序或被代理到远端的本机 socket。

镜像固定为公开 Oracle MySQL `docker.io/library/mysql:5.7.44`、`docker.io/library/mysql:8.0.40`；执行前从公开 registry 拉取精确 tag，记录 image ID，按 ID 创建容器，实际 VERSION 必须精确相等，vendor 必须为 `MySQL Community Server - GPL`。本次未拉取，镜像可获取性/实际 VERSION/image ID 均 `UNKNOWN`。8.0.40 是演练固定对照版本，本文不据此声明其当前生产支持期限或推荐它作为生产升级版本。

容器使用 task 前缀加随机后缀、task 与随机 nonce 双标签；创建前拒绝同名已有容器/卷。`--network none`、mysqld `--skip-networking`，不发布端口、不挂载主机目录、不用 host PID/network/privileged。数据目录为 512 MiB tmpfs，内存上限 1 GiB，CPU 上限 2；不创建持久卷。随机临时口令只在运行内存、容器环境和 stdin 中使用，不进入 argv、日志、Git 或证据。等待 PID 1 成为最终 mysqld，避免误用 entrypoint 初始化临时服务器；SQL 只经容器内部 Unix socket。

每次 SQL 前验证容器 ID、名称、归属标签和隔离设置；异常、启动失败、SQL 执行中断均进入 finally 清理，只按已确认归属的容器 ID `docker rm -f`，不删除他人的卷/实例。create 超时尝试按本次 nonce 恢复归属；无法确认创建结果或删除失败标记 `FAILED_MANUAL_REVIEW_REQUIRED`，不得输出完成结果。硬断电/SIGKILL、daemon 永久失联仍可能需要人工按本次标签核对残留，不能声称清理必然成功。

## 精确覆盖矩阵

下列每个对象的“5.7/8 结果”均指**真实引擎**。`NOT_RUN` 不等同失败或兼容。静态历史风险不是本次观察到的差异。

| 对象（9 表 / 2 列 / 2 索引） | 读取路径与行号 | 5.7.44 结果 | 8.0.40 结果 | 差异 | 是否可用于生产结论 |
| --- | --- | --- | --- | --- | --- |
| draft_lifecycle_event | sql/updatesql/20260928/01_create_draft_lifecycle_event.sql:4 | NOT_RUN | NOT_RUN | UNKNOWN | 否 |
| research_plan | backend/migrations/20261001_research_plan.sql:3 | NOT_RUN | NOT_RUN | 命名 CHECK/JSON/TIMESTAMP(6) 真实行为 UNKNOWN | 否 |
| risk_watch_rule | backend/migrations/20261002_risk_watch.sql:3 | NOT_RUN | NOT_RUN | 同上，UNKNOWN | 否 |
| risk_watch_snapshot | backend/migrations/20261002_risk_watch.sql:11 | NOT_RUN | NOT_RUN | 同上，UNKNOWN | 否 |
| risk_watch_event | backend/migrations/20261002_risk_alert_history.sql:2 | NOT_RUN | NOT_RUN | 同上及 nullable timestamp，UNKNOWN | 否 |
| risk_watch_mute | backend/migrations/20261002_risk_alert_history.sql:12 | NOT_RUN | NOT_RUN | 无 payload CHECK；NULL/时间真实行为 UNKNOWN | 否 |
| allocation_policy | backend/migrations/20261002_allocation_policy.sql:3 | NOT_RUN | NOT_RUN | 命名 CHECK/JSON/TIMESTAMP(6) 真实行为 UNKNOWN | 否 |
| goal_tracking | backend/migrations/20261002_goal_tracking.sql:3 | NOT_RUN | NOT_RUN | 同上，UNKNOWN | 否 |
| monthly_budget | backend/migrations/20261002_monthly_budget.sql:3 | NOT_RUN | NOT_RUN | 同上，UNKNOWN | 否 |
| settlement_confirm.preview_digest | sql/updatesql/20260929/01_settlement_audit_link.sql:3 | NOT_RUN | NOT_RUN | ALTER/重跑状态 UNKNOWN | 否 |
| settlement_confirm.ledger_txn_id | 同文件:4 | NOT_RUN | NOT_RUN | 同一 ALTER 的第二列，UNKNOWN | 否 |
| uk_draft_ledger_user_source | sql/updatesql/20260927/03_add_draft_ledger_source_unique_keys.sql:16 | NOT_RUN | NOT_RUN | 三列顺序、唯一性、NULL/重复行为 UNKNOWN | 否 |
| uk_draft_ledger_family_source | 同文件:21 | NOT_RUN | NOT_RUN | 家庭 NULL/重复/部分应用状态 UNKNOWN | 否 |

数量闭环：`1+1+2+2+1+1+1=9` 表，结算 ALTER 含 `2` 列，独立 ALTER 各含 `1` 个唯一键，共 `2`。额外读取 `sql/initsql/20260610_draft_ledger_entry.sql:4` 作为**既有依赖原文语法试验**，不计入缺表；读取 normalization 文件作为隔离合成数据示例，不计入 DDL 缺口。没有无差别拼接历史 updatesql，没有混跑互斥 init/migration；历史 SQL 保持不变。

## 工具准备的真实引擎场景（本次全部 NOT_RUN）

| 场景 | 5.7 结果 | 8.0 结果 | 差异与生产用途 |
| --- | --- | --- | --- |
| 9 表逐条原文 CREATE、重跑、成功表 SHOW CREATE | NOT_RUN | NOT_RUN | UNKNOWN；只能描述临时库解析与结构 |
| 含 CHECK 的 7 表：有效 JSON、非法文本、空串、JSON 0/JSON null、SQL NULL | NOT_RUN | NOT_RUN | UNKNOWN；建表失败必须 SKIPPED_CREATE_FAILED，不得声称约束有效 |
| TIMESTAMP(6) 的 123456 微秒、owner=0/family=NULL、mute NULL 时间 | NOT_RUN | NOT_RUN | UNKNOWN；只验证选定合成值 |
| 草稿依赖原文建表；唯一键使用显式最小 synthetic fixture | NOT_RUN | NOT_RUN | UNKNOWN；fixture 成功不能替代实际 draft schema 兼容结论 |
| 结算两列 ALTER、重复列错误、SHOW FULL COLUMNS | NOT_RUN | NOT_RUN | UNKNOWN；空 synthetic 表不等同正式结算业务 |
| 两索引的 SHOW INDEX、精确六行列顺序与 NON_UNIQUE=0 | NOT_RUN | NOT_RUN | UNKNOWN；不证明生产索引存在 |
| owner/family 重复、不同作用域、NULL 来源、空串/空白、来源 0 | NOT_RUN | NOT_RUN | UNKNOWN；排序规则为显式 utf8mb4_unicode_ci，生产规则未知 |
| 同 family 来源冲突：第一 ALTER 成功，第二 ALTER 失败；核对部分索引状态 | NOT_RUN | NOT_RUN | UNKNOWN；独立记录每条返回码与后续状态，不把 1061 当整体已应用 |
| normalization 原文 UPDATE，前后空来源计数及 updated_at 影响 | NOT_RUN / SKIPPED | NOT_RUN / SKIPPED | 仅 fresh schema+synthetic fixture 时可执行；未对已有库运行 |
| START TRANSACTION / CREATE / ROLLBACK 后 SHOW TABLES | NOT_RUN | NOT_RUN | UNKNOWN；示例不提供 DDL 事务恢复保证 |

每条 SQL 分别记录返回码、SQLSTATE、错误号和固定错误类别，不保留原始错误正文。只输出刚创建库的合成 SELECT/SHOW 摘要。约束不强制、解析失败、重复键/列/索引均可分辨；证据包含 expectation_met/summary_expectation_met，完成执行也只标 `EXECUTED_REVIEW_REQUIRED`，不自动给引擎或生产 PASS。

最小依赖 draft 只含来源相关字段与时间，settlement 只含 id；无真实基础业务表、账户、资金、订单或记录。部分应用示例后仅删除本次合成草稿行，再试第二个索引。normalization 确实会更新 updated_at；原业务 UPDATE 不因“空值等价”变成无风险。本次在引擎门禁处停止，连合成 UPDATE 也没有执行。

## 已执行验证与证据

| 实际命令 / 证据 | 结果 |
| --- | --- |
| `python -m unittest discover -s scripts -p test_migration_preflight.py -v` | 6 项通过 |
| `python -m unittest discover -s scripts -p test_mysql57_compat_preflight.py -v` | 8 项通过 |
| `python -m unittest discover -s scripts/deploy -p test_mysql_isolated_rehearsal.py -v` | 25 项通过；全部为离线安全/工具行为验证 |
| 默认 CLI + `--output scripts/deploy/mysql_isolated_dry_run_20261010.json` | Python 退出 0，DRY_RUN，9/2/2，两个引擎 NOT_RUN |
| 显式 `--run-isolated --output scripts/deploy/mysql_isolated_engine_evidence_20261010.json` | 安全阻断，Python 设计退出 2；没有 Docker，两个引擎 NOT_RUN，events 为空 |
| `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0` | 退出 0，后端 Maven package、shared/PC/mobile 前端 build 通过；成功仅 stdout/log，无声音或弹窗。hook 的 Maven 命令跳过后端单元测试，不能据此声称后端测试通过。日志 `.codex-hooks/logs/latest-build.log` |
| `git diff --check`、`git diff --cached --check` | 通过；暂存范围仅 6 个 allowed_paths 内新增文件 |

离线测试覆盖默认/help 无进程、拒绝真实地址参数/非白名单文件/文件 hash 漂移/输出越界或覆盖、context/env 重定向、本机端点白名单、同名已有容器/卷、镜像失败、固定 9/2/2、实例标签/ID/隔离设置、socket/临时密码传递、容器终止、启动失败、create 超时、执行中断、清理失败/创建状态未知、vendor/version 不匹配、错误脱敏、CREATE 失败后跳过 payload，以及无 Docker 时不报 PASS。**测试替身不是 MySQL，不产生 Oracle 引擎证据。**

真实生产的其余字段/索引、数据重复、存储引擎、字符集/排序规则、索引大小、锁表时间、备份恢复性及完整应用兼容性全部保留 `UNKNOWN`。没有本次实际 VERSION、镜像 ID 或 SQLSTATE，不能填入静态预测值。

## 人工选择与交付边界

本次没有满足 `ENGINE_NOT_TESTED` 的解除条件。下一阶段可由 owner 在可信本机 Docker 及有效授权范围内运行新证据，再比较原文解析、JSON 实际拒绝、时间与唯一键行为。路径 A 是另行规划迁移至生命周期得到确认的受支持 MySQL 8 环境；路径 B 是另行设计、审查 5.7 兼容 migration 及等价的数据校验方案。两者都仍需要实际生产 schema/数据风险核验、备份恢复演练和独立生产变更授权。工具没有生成生产批处理，也没有执行升级或部署。

本地交付 manifest：`scripts/deploy/isolated_engine_delivery_manifest_20261010.json`；结果提交使用 `git log -1 --format=%H -- docs/mydca_mysql57_80_isolated_engine_rehearsal_20261010.md` 定位。AiCore Delivery Manifest 发布及独立 Hermes/企业微信 verified receipt 均交给外部交付链；当前没有可调用的投递工具，allowed_paths 不允许写入该链的外部目录。没有冒造通知/收据，外部交付状态保留 `PENDING_EXTERNAL_CHAIN / NOT_VERIFIED`。
