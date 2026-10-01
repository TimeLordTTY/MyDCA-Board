# v0.16 研究方案后端

研究方案是系统内研究元数据。历史研究不代表未来表现。不会生成买卖指令、订单、结算或账本，不修改策略注册表，不运行回测。

## API

所有接口复用登录认证，数据必须同时匹配当前 user 和 family（包括 null family）。同家庭其他 owner 也不可访问。

| 方法 | 地址 | 行为 |
| --- | --- | --- |
| POST | `/api/v2/research-plans` | 从已保存回测重新校验候选并复制快照 |
| GET | `/api/v2/research-plans?page=0&size=20` | 分页，只读 |
| GET | `/api/v2/research-plans/{id}` | 详情，只读；不可见返回 404 |
| PATCH | `/api/v2/research-plans/{id}` | 修改名称、备注、参数草稿或研究状态 |

创建请求：`name`（必填，最多 120 字符）、`description`（可空，最多 4000 字符）、`runIds`（1–50 个不同 ID）、`thresholds`（可空，复用候选研究阈值）、`candidateId`。候选研究接口新增 `candidate_id`，是排序后的 canonical params、strategy/version 的 SHA-256；同一研究对象在不同 run 选择中可以有同一候选引用，实际来源集合由 `sourceRunIds` 固定。

创建时重新计算候选，禁止提交任意客户端证据。未成功或越权 run 不可作为来源。结果存储 owner、名称、备注、DRAFT、来源候选/运行 ID、策略/版本、canonical 参数快照、dataset hashes、完整 allowlist 证据快照、阈值/门禁版本、创建和更新时间。`evidenceBundleRef` 是内嵌 `evidenceSnapshot` 紧凑 UTF-8 JSON 的 SHA-256 引用，不是外部 ZIP 下载地址；单 run 也可保存。创建后候选重新筛选、源文件变化或删除不会覆盖快照。

PATCH 字段全部可选：`name`、`description`、`paramsDraft`、`status`。备注用空字符串清空。参数草稿允许 JSON 对象，最多 50 个顶层属性、8000 字节；只保存研究想法，不保证能够作为回测输入。`canonicalParamsSnapshot` 始终不变。

状态仅 `DRAFT` / `ACTIVE` / `ARCHIVED`，初始 DRAFT。DRAFT 和 ACTIVE 可互相切换或归档；ARCHIVED 是终态，禁止任何编辑。并发编辑用原始 payload 的二进制比较防止覆盖，冲突返回 409，刷新后重试。非法输入返回 400，存储/证据读取 IO 故障返回 503。

GET 和列表只做 SELECT 和本地历史/数据集读取，不创建目录、不更新警告、不写业务数据。读取时源 run 丢失/失败、安全证据改变、数据集不存在或 hash 改变均产生中文 `warnings`；证据读取故障明确失败，不伪装成有效。创建和修改也返回当前警告。数据集仅在既有本地 `backtest.data-root` 目录内检查，不访问生产数据库或外部行情。

## 持久化与人工部署

增量迁移文件：`backend/migrations/20261001_research_plan.sql`；新安装通用初始化文件：`backend/sql/initsql/research_plan.sql`。二者提供相同结构，按安装场景择一执行，不得同时执行。由于本任务仅允许 backend/docs/README，未修改仓库根 sql 目录。仅新增一个 `research_plan` 表；4 条 Mapper SQL 都约束 owner user/family，无财务表引用。payload 为校验 JSON 的 LONGTEXT，保留字节形式支持证据 hash 与并发检查；owner 索引支持分页。

脚本不接入 Flyway/Liquibase，不在启动时执行。本任务未部署迁移、未连接数据库。上线前由部署方人工审核、备份、在目标 MySQL 8 环境执行此脚本并验证权限。未建表时接口会存储失败；不得宣称脚本存在等于上线可用。数据库集成验收和 UI 接入仍需后续授权任务。

## 验证

`ResearchPlanServiceTest` 使用临时本地历史/数据集与 mock Mapper，覆盖 user/family 越权、创建证据复制、只读访问不写入、参数编辑无回测副作用、数据集变更/删除、run 删除、非法状态、归档终态及交易服务依赖隔离。现有候选回归测试保留。

2026-10-01 本地后端 `mvn test`：205 项，0 失败、0 错误、0 跳过。新增测试同时验证来源证据变更/失败、并发编辑冲突和 MyBatis 的四条 SQL 参数绑定（包括 null family 的 `<=>` 条件）。完整 `scripts/post-task-compile-hook.ps1`（后端 package + 前端 build）通过，成功仅 stdout/log；`git diff --check` 通过。默认 PowerShell 的进程启动出现环境变量错误，改由 cmd 启动同一条要求命令后通过，未修改 hook。迁移只生成不部署，没有真实数据库测试。
