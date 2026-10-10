# 财富中枢生产备份、结构迁移与部署（2026-10-10）

用户直接授权完整生产备份、必要结构迁移、最新前后端服务部署；现有财务数据修改必须另行确认。本次跳过来源归一化及所有历史修复 DML，不执行交易或正式入账。

## 已核验的数据库备份

服务器 `baota-124`；应用根目录 `/www/wwwroot/wealth-hub`。迁移前完整备份目录 `/www/wwwroot/wealth-hub/backups/production-full-20261010-103042`，仅服务器 root 可访问。

- MySQL 实测 `5.7.44-log`，31 张基础表全部 InnoDB。
- `database.sql.gz`：1,982,705 bytes，SHA-256 `2fb21257d5d7415455254802a36b172260ff7daefa78e1d98b8c71a0983f740c`；使用 single-transaction 导出全体基础表结构及数据、触发器；独立保存并合入全部 4 个 SHOW CREATE VIEW 原始定义。存储过程、函数、事件实测均为空。
- SQL 全部解压读取、基础表及视图定义覆盖验证通过，未压缩 SQL 13,840,221 bytes；每表导出行数保存在服务器 manifest，不返回业务记录。
- `application.tar.gz`：30,796,611 bytes，SHA-256 `60858a837cba1df612bcefe110de4da1769d353c5a9191c560dc079392382f4b`；保存当前 JAR、完整 PC/H5 前端、backend/config 及存在的数据目录，逐成员读取验证通过。
- 恢复演练 **NOT_RUN**：应用账号不能创建独立数据库，既有面板管理凭据认证失败；未重置口令或更改权限。生产视图存在历史 SELECT/definer 权限问题，原定义完整保留，未擅自修复。不能据压缩校验宣称恢复演练通过。
- 较早失败备份目录保留，不作为可用完整备份；最终有效备份以此目录及 manifest 为准。

## 已执行的生产结构迁移

迁移前临时表演练通过：9 张表逐条实际 CREATE；7 个 JSON payload 有效数据读写通过，非法 JSON 实测错误码 3140；使用真实 settlement_confirm/draft_ledger_entry 定义的临时副本验证 ALTER 与两个唯一键。临时对象随连接关闭消失，不修改既有记录。

2026-10-10 10:35:20 +08:00，实际执行 12 条 DDL：

| 内容 | 实际结果 |
| --- | --- |
| draft_lifecycle_event | 创建成功，来源 sql/updatesql/20260928/01_create_draft_lifecycle_event.sql |
| research_plan、risk_watch_rule、risk_watch_snapshot、risk_watch_event、risk_watch_mute、allocation_policy、goal_tracking、monthly_budget | 创建成功，使用本次新增 MySQL 5.7 路径 |
| settlement_confirm.preview_digest、ledger_txn_id | 添加两个可空字段；未回填历史记录 |
| uk_draft_ledger_user_source、uk_draft_ledger_family_source | 两个唯一索引成功，实测 6 条列定义、NON_UNIQUE=0、列顺序正确 |

7 张尚不存在的 payload 表使用原生 JSON 替代 LONGTEXT + CHECK，因为 MySQL 5.7 会解析但忽略 CHECK。不改写历史迁移，不转换已有表；JSON 文本可能规范化键顺序，应用用 Jackson 解析业务内容。研究方案的乐观锁直接复用数据库读出的原始 previous 文本；真实临时表验证 BINARY payload 更新成功、旧值再次更新被拒绝，未改动正式元数据。该路径与原始建表路径互斥。

生产空来源行数、个人重复来源组数、家庭重复来源组数均为 0，归一化 UPDATE 完全跳过。迁移后 9 张新表均为空。11 张相关表（账户、两类账本、订单、资金线、结算、持仓、草稿、债务合同/分期、净值快照）在一致性只读快照中对原有字段逐行 SHA-256，迁移前后完全一致。新增结算列从指纹输入排除，以避免把新增 NULL 字段误报为历史数据改写。

脱敏迁移证据与源 SQL 哈希保存在完整备份目录的 `migration-preflight.json`、`migration-executed.json`、`migration-result.json`；含业务表哈希的详细文件仅保留在服务器受限目录。

## 部署与验收

首次部署新 JAR SHA-256 `a152e8a120a8a1d5af4a436582108323e67d4c8689fc3fbc934281f9c9e083d5` 启动失败：BacktestLabService 多构造函数未明确 Spring 注入入口，NoSuchMethodException。标准脚本实际自动恢复旧 JAR 与完整前端并确认后端 UP；备份 `/www/wwwroot/wealth-hub/backups/20261010-103811-Z5f47k`，失败证据 `/tmp/mydca-release.Z5f47k`。生产结构迁移保留，不做破坏性 schema 回滚。

修复限定 BacktestLabService 的生产构造函数 @Autowired，并新增真实 AnnotationConfigApplicationContext 实例化回归；同时将默认回测解释器设为 python3，匹配服务器实际安装。7 项相关后端测试通过；部署离线回归 7 项（多场景），生产双目录成功/回滚新增回归 1 项，PowerShell 打包验证通过，完整构建 hook 通过。

第二次切换实际成功，后端 UP；JAR SHA-256 `d0cef1d7b0467e2de9cea4364140d85ed2c5a25e70207b30665e8921c27592c6`，旧包备份 `/www/wwwroot/wealth-hub/backups/20261010-104240-qPjceG`。既有测试账号的登录、用户、移动总览、账户/详情、流水、持仓只读 smoke 全部通过，未登录为 401。随后还需将 python3 默认入口的最终构建部署并重新验收。

服务器缺少回测运行文件，补齐仓库原文 `scripts/backtest/run_backtest.py`、`core/backtest/engine.py`、`core/backtest/__init__.py`；仅部署程序并检查 Python3 import/--help，不导入数据、不执行生产回测。现有脚本的其他目录未覆盖。

## 最终线上验收

2026-10-10 10:46 +08:00 完成最终切换，标准脚本退出 0，`deployment_status=success`、后端 JSON `status=UP`、新 Java PID `905649`。最终运行 JAR SHA-256：

`0f54b41430c150004d7c71a2667898d204b513b535c759bd5be25e0f435e39bf`

最终切换前的完整旧 JAR/前端备份：`/www/wwwroot/wealth-hub/backups/20261010-104617-J2fYxm`。最终运行 JAR 与本机构建哈希相等，仅有一个进程运行该 JAR。

| 项目 | 实测结果 |
| --- | --- |
| https://www.timelordtty.cn/wealth-hub/ | HTTPS 正常校验证书，200，首页 SHA-256 a7546fac99de8c0144dda9bebca044f7803e6ad22b2d6a078da99e73caeb4e20，与本次构建一致 |
| https://www.timelordtty.cn/wealth-hub-mobile/ | HTTPS 正常校验证书，200，首页 SHA-256 f249b62598663eceadc4fce3828e29692cc0c578d8e598f64f160da2d4d2fb5d，与本次构建一致 |
| PC/H5 首页引用的 6 个 JS/CSS | 全部 HTTP 200，下载内容与生产实际文件逐一 SHA-256 相等 |
| 既有隔离账号登录 | 200；不注册或创建账号，不输出凭据/Token |
| 用户、移动总览、账户、流水、持仓 | 5 个 GET 全部 200，JSON 类型正确；另一次移动只读 smoke 还验证账户详情和分页契约 |
| 研究方案、风险规则、配置策略、目标、预算、个人数据诊断 | 6 个 GET 全部 200，JSON 类型正确 |
| 回测数据集、最近回测、历史列表、草稿列表 | 4 个 GET 全部 200，JSON 类型正确；未执行回测或创建草稿 |
| 未登录访问移动总览 | 401 |
| 11 张受保护表的原有字段数据 | 最终部署后的只读一致性快照与迁移前 SHA-256/行数全部一致 |

最终脱敏证据：完整备份目录内 `final-live-verification.json`；运行文件备份/哈希：`backups/runtime-backtest-20261010-104311/manifest.json`。前端实际交付 PC 与兼容 H5，不涉及 Android APK 更新。

本次结论为**服务实际部署成功、结构缺口已补齐、财务数据未改写**。独立数据库恢复演练未通过/未执行，真实角色写入与交易/正式入账未测试，不把测试账号的只读访问当作全部业务功能验收；数据库仍为 MySQL 5.7，未执行服务器数据库版本升级。源码及此报告提交至 MyDCA-Board v2，AiCore 另存前台实执行报告，不冒造自动任务 Manifest 或企业微信收据。
