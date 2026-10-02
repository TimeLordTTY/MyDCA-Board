# v0.19 目标与预算发布硬化

日期：2026-10-02 +08:00。任务：`task-mydca-v019-release-hardening-20261002`。

## 交付范围与结论

依赖交付已在本地树：长期目标后端 `3d85e91`、月度预算后端 `981b907`、Android 只读查看 `5fb5319`、PC 中心 `a2a01ca`。本地工程回归通过；目标环境 schema、真实设备/浏览器体验和 CI 制品仍待后续验收，不表示已经生产发布。

Android 版本统一为 `0.19.0 / versionCode 20`，aapt2 对本地 APK 实测一致。workflow 预期制品 `mydca-android-v0.19.0-<full-sha>`，APK `MyDCA-Board-v0.19.0-<short-sha>.apk`。本任务未推送或启动 CI，不记录虚构的 Run ID、Artifact ID 或 CI APK SHA-256，本地构建不作为 CI 成功证据。

本次修正 Android 英文作用域为个人/家庭；预留项显示「仅计划，不对应实际资金划转」，收入项明确不适用超支判断；支出仍按 quality 和 nullable overspent 显示未知。加载/错误增加 polite liveRegion 播报。补充中文标签、未知边界以及模拟断网失败清除旧数据、成功重试恢复的状态测试。README、当前状态、文档索引和 Android 说明同步。

## 回归与安全证据

| 范围 | 实现与回归证据 | 结果 |
| --- | --- | --- |
| 目标生命周期 | `backend/src/test/java/com/timelordtty/dca/service/GoalTrackingServiceTest.java`、`web/pc-app/tests/goalBudgetCenter.test.mjs` | 创建/编辑、暂停/恢复/归档保留 createdAt；各状态允许只读进度；owner/current-family 和家庭管理员隔离通过 |
| 目标统计 | `GoalTrackingService.observe` 及测试 | 等号完成、超过100%、未舍入金额比较、目标日不过期、前后日期、独立现金/市值口径、总额矛盾与错 scope、非CNY不换汇通过 |
| PARTIAL/UNKNOWN | 两个后端 service 测试、PC 模型测试、`GoalBudgetRepositoryTest.kt` | 部分已知单列，最终金额/完成率/超支保留 null；未知不当零；完整读取空流水才是已知零 |
| 预算计划/实际 | `MonthlyBudgetServiceTest.java` | 收入/固定/弹性/预留、退款净额、负计划结余、单项及整体超支、闰月和跨年半开区间、缺失金额/分录/方向/日期/币种、未匹配分类通过 |
| PC 展示与错误态 | `GoalBudgetCenter.vue`、五项专项回归 | 元数据表单校验、编辑失败保留输入、403 清除旧进度、刷新失败清除旧预算证据、分页/空态与重试通过；label/fieldset、status/alert、focus-visible 已静态核对 |
| Android 展示 | `GoalBudgetScreen.kt`、三项 `GoalBudgetRepositoryTest.kt` | BigDecimal、分页、本月筛选、401/403/503、损坏响应、GET白名单与认证、未知展示、中文作用域、预留/收入语义、错误清除及模拟离线恢复通过 |
| 数据库配套 | 两个 Mapper、四个 SQL 脚本 | goal_tracking 四条语句、monthly_budget 五条语句；元数据只 INSERT/UPDATE 对应表，财务仅 SELECT。两个 init 与各自 migration 字节相同；没有自动部署 |

目标接口五类路径：POST 创建、GET 分页/详情/进度、PATCH 配置；预算同样五类路径，进度改为 comparison。PC 可写规划元数据；Android 仅四个 GET 列表/观察接口，无创建、编辑、交易、preview/confirm 或结算入口。目标状态是元数据，暂停/归档不冻结资产快照，不自动完成目标。

财务读取链为 `GoalTrackingService.progress → FinanceRadarService.getRadar` 与 `MonthlyBudgetService.comparison → MonthlyBudgetMapper.facts`，两个观察入口均只读事务。goal/budget 服务依赖不包含 OrderService、LedgerService、SettlementService 或券商接口；只写规划表，不写账户/持仓/账本/订单/结算，不创建自动转账、不占用资金/份额。预算只读当前 owner 的已确认、未撤销收入/支出及报销分录，严格绑定 family；预留只是计划。既有其他模块人工 DRAFT/preview/二次确认边界保留。本次没有连接数据库、执行 SQL、修改真实财务记录或调用交易渠道。

计划结余扣除预留；实际结余不扣计划预留。总体实际和超支在任何不完整证据下保持未知；完整项目仍可单独显示结果。人民币资产进度不扣负债；其他币种目标未知且不换汇。Android 按设备当前月份筛选分页结果，未匹配页可以继续翻页，不代表所有月份预算为空。

## 本地验证

| 命令/检查 | 真实结果 |
| --- | --- |
| backend `mvn -B test` | 32 测试类 / 233 项，0 失败、错误、跳过；目标4项、预算6项；mock Mapper，无数据库 |
| web `node --test pc-app/tests/*.test.mjs` | 28 项通过，目标/预算5项；mock API，不使用真实账号 |
| web `npx tsc --noEmit -p pc-app/tests/tsconfig.goalBudget.json` | 专项类型检查通过；不宣称全仓 vue-tsc 通过 |
| Android `gradlew.bat testDebugUnitTest assembleDebug lintDebug --no-daemon` | 44 类 / 292 项，0 失败、错误、跳过；APK 成功；lint 0 error / 2 既有 warnings / 14 information |
| `aapt2 dump badging app-debug.apk` | versionCode 20 / versionName 0.19.0 |
| `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0` | 后端 package、shared/PC/mobile 构建通过，成功仅 stdout/log |
| `git diff --check` | 通过 |

npm ci 准备本地依赖，锁文件未改；Android 使用进程 ANDROID_HOME 指向本机 SDK，不写 local.properties。hook 由 cmd /c 启动 Windows PowerShell；关闭自动修复轮次以确保修改仅落在 allowed_paths。没有修改 hook，其既有成功静默行为保持不变。

## 后续人工验收

- 数据库：`backend/migrations/20261002_goal_tracking.sql`、`20261002_monthly_budget.sql` 仅提交未部署；初始化脚本位于 `backend/sql/initsql/`，按环境择一人工部署。mock 测试不证明 MySQL schema 或部署已通过。
- 体验：PC 真实登录与键盘、Android TalkBack/分页/本月边界、设备实际断网→错误→恢复网络→人工重试仍需验收。离线自动测试验证状态转换，未模拟真实无线网络。
- 制品：仅未来真实 CI 成功后回填 source commit、run、artifact 与 hash。

本任务只本地提交，不 push；goal_only 企业微信汇总由 AiCore/Hermes 交付，本执行器不主动发送消息、不宣称通知已送达。
