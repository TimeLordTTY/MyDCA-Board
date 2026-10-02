# v0.17 风险观察中心发布硬化

任务：`task-mydca-v017-release-hardening-20261002`。2026-10-02，本地工作树验证与提交，不推送、不部署、不连接生产数据库、不修改真实财务记录。

## 发布结论

v0.17 工程回归通过；生产发布仍须人工确认迁移、真机体验和 CI 制品。后端规则评估、提醒历史、PC 与 Android 四项依赖已包含于当前树：`9743309`、`9803f30`、`29dca8b`、`d248102`。

Android 版本收敛为 `0.17.0 / versionCode 18`，本地 APK 经 `aapt2 dump badging` 实测一致。workflow 预期 artifact 为 `mydca-android-v0.17.0-<full-sha>`，APK 为 `MyDCA-Board-v0.17.0-<short-sha>.apk`。本次未推送或运行 CI，无真实 CI Run / Artifact / APK SHA-256 证据，本地 APK 不作为 CI 交付证据。

修复 Android 合法观察备注快照（OK、无阈值、无数值）误报 UNKNOWN；真实 UNKNOWN 仍保留未知提示。补充规则类型、作用域、方向的中文文案，加载/失败文本使用 Compose polite live region。补充针对备注与 UNKNOWN 的回归断言。PC 保留中文错误、手动重试、原生 label、status/alert 与键盘焦点样式。

## 回归与安全证据

| 检查 | 证据与结果 |
| --- | --- |
| 六类规则、等号阈值、未知、陈旧行情/指标 | `backend/src/test/java/com/timelordtty/dca/service/RiskWatchServiceTest.java:29` 覆盖收益、回撤、集中度、类别偏离、陈旧、备注；未知不转换为零，回撤指标超过 3 天未知 |
| owner/family 隔离与手工评估 | `backend/src/main/java/com/timelordtty/dca/service/RiskWatchService.java:33` 家庭管理员校验；`RiskWatchMapper.java:12` 等全部读写均带 owner AND family；快照重用与并发冲突由 mock 单测覆盖 |
| fingerprint 去重、OPEN/RESOLVED、已读/静默 | `RiskWatchService.java:141` UNKNOWN 不 resolve；`:161` 优先级 RESOLVED → MUTED → ACKNOWLEDGED → OPEN；`RiskWatchMapper.java:27` 去重写入不覆盖历史证据；生命周期单测覆盖完整链路 |
| PC 展示、刷新、分页、失败恢复 | `web/pc-app/tests/riskCenter.test.mjs:26` 失败清除旧证据、重试恢复；表单边界、UNKNOWN、陈旧、提醒状态、显式 ACK/MUTE、API 安全限制全部通过。`RiskCenter.vue:50` 完整读取成功后发布证据；刷新不 evaluate |
| Android 展示、分组、过滤、历史、offline/retry | `RiskWatchRepositoryTest.kt:15` MockWebServer 验证认证 GET、无请求体、DTO、401/403/503、损坏响应；`:69` 网络失败与取消传播；`:86` 分组/状态与新鲜度。`RiskWatchScreen.kt:29` 前台读取并清除旧状态，部分读取失败不伪装正常完整汇总 |
| 无财务写入口 | 风险 Controller 仅依赖 RiskWatchService；该服务依赖 RiskWatchMapper 及现有财务只读查询。财务读取沿 `FinanceRadarService.getRadar`、`HoldingService.calculateHoldings`、`IndicatorService.getLatestIndicator`，无订单创建/交易/结算/账本写调用。RiskWatchMapper 仅写四个 risk_watch 元数据表 |
| 客户端安全边界 | `web/shared/src/api/riskWatch.ts:5` 所有请求限定 risk-watch-rules；PC 只有显式规则/评估/已读/静默操作。`android-app/app/src/main/java/com/timelordtty/mydca/data/api/WealthHubApi.kt:39` 风险接口全为 GET；Repository 不评估、不 ACK/MUTE，无执行动作 |

风险观察只读指财务数据只读，允许保存观察元数据。既有应用的人工草稿确认与人工结算入口不属于风险模块；其二次确认边界未变更。跨雷达/研究页面的 readonly 参数仅表达观察跳转，不宣称它是服务端权限隔离机制。

过滤仅针对已加载页；Android 总览汇总本页规则各最近 20 条，不代表完整未解除清单。快照为已保存证据，刷新 GET 不等于重新评估。SOURCE 日期提示不证明行情实时可用。

## 迁移与完整性审查

`backend/migrations/20261002_risk_watch.sql`、`backend/migrations/20261002_risk_alert_history.sql` **仅提交，未自动部署**。四表为 rule / snapshot / event / mute；只有观察元数据，无财务 DML。新安装可择一使用对应 `backend/sql/initsql/risk_watch.sql` 和 `risk_alert_history.sql`，内容与增量一致；不能两套重复执行。不存在自动迁移注册或本任务 SQL 执行。

使用 commit-branch-consistency-review 枚举依赖范围 `9535d69faa4c5e13d76e5718d740fb3c8c607377..d248102f0e442ae5e6740a9db40d3efc6a95f873` 的 39 个文件，并枚举目标树全部 Mapper/SQL。未刷新远端。完整清单与逐对象矩阵见 [覆盖附录](mydca_v017_release_coverage_20261002.md)。XML Mapper 20 + 注解 Mapper 2 = 22；SQL 语句 151；Mapper 对象 25 = 有脚本覆盖 25 + 无覆盖 0；SQL 文件 36；新增对象 4，全部有初始化/增量；未解析和动态表名均 0。脚本对象候选 63 包含函数/注释，不等于实际建表数。全量枚举不代表全仓业务语义审查通过。

| 编号 | 等级 | 文件与位置 | 证据、影响及处理 |
| --- | --- | --- | --- |
| DB-001 | 警告（既有） | `sql/updatesql/20260610/01_create_draft_ledger_entry.sql:4` | draft_ledger_entry 仅有增量建表，通用 init 缺失。新安装需人工纳入草稿增量，不能只跑通用 DDL；建议另行补齐初始化并在隔离环境验证。本次不修改范围外 SQL |
| ENV-001 | 待人工验收 | 两个 v0.17 migration | 本地 mock 测试不证明目标 schema 可用。部署方审核并在授权环境验证迁移与 owner/family 权限后再验收 |
| UX-001 | 待人工验收 | `RiskWatchScreen.kt`、`RiskCenter.vue` | 未执行真机 TalkBack、真实键盘/登录与目标接口体验；需要断网 → 错误 → 联网手动重试、分页及权限场景人工验收 |
| CI-001 | 待真实证据 | `.github/workflows/android-test-apk.yml:43` | 命名已对齐，未运行 CI；后续仅成功 run 才能回填 Run / Artifact / APK hash |

本次范围内无未修复工程阻断；整体为工程回归通过、发布有条件通过，不声称已部署或已完成生产验收。此前 PC 专项记录全量 vue-tsc 的既有类型诊断；本轮按既有 Vite build/hook 验证，未重跑或宣称全量类型检查通过。

## 本地验证

| 命令 | 真实结果 |
| --- | --- |
| backend：`mvn test` | 211 项，0 失败/错误/跳过；不启动数据库 |
| web：`node --test pc-app/tests/*.test.mjs` | 17 项全部通过（风险中心 6 项） |
| android-app：`gradlew.bat testDebugUnitTest assembleDebug lintDebug --no-daemon` | 42 个测试类 / 285 项，0 失败/错误/跳过；APK 构建成功；lint 0 errors / 2 既有 warnings |
| `aapt2 dump badging .../app-debug.apk` | versionCode='18'、versionName='0.17.0' |
| `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0` | 后端 package 与 shared/PC/mobile 构建通过；成功只写 stdout/log |
| `git diff --check` | 通过 |

初轮 PC 因工作树缺少依赖/shared dist 失败，`npm ci` 与 hook 构建后全量重跑通过；锁文件未变更。初轮 Android 缺 SDK 路径，使用本机 SDK 的进程 ANDROID_HOME 后重跑通过，未写 local.properties。hook 禁用自动修复轮次避免自动修复越出 allowed_paths，构建本身完整执行。

仅本地提交；不编辑 agent-inbox、不 push、不部署。goal_only 企业微信汇总交付由 AiCore/Hermes 负责，本任务未主动发送通知。
