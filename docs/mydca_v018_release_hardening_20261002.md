# v0.18 配置偏离与止盈观察发布硬化

任务：`task-mydca-v018-release-hardening-20261002`；2026-10-02。仅 allowed_paths 内的工程变更与本地提交，不推送、不部署、不连接生产数据库、不修改真实财务记录、不编辑 agent-inbox。

## 发布结论与版本

v0.18 依赖工程已包含：配置后端 `59adbd3`、情景引擎 `4125854`、PC `31d31b9`、Android `18f2887`。定位保持“主人自定义规则 + 只读数学情景预览”。工程回归通过，生产发布有条件通过；目标 schema、真机体验和真实 CI 仍待人工验收。

Android 收敛为 `versionName=0.18.0 / versionCode=19`，本地 APK 用 aapt2 实测一致。CI 命名为 `mydca-android-v0.18.0-<full-sha>` / `MyDCA-Board-v0.18.0-<short-sha>.apk`。本次未推送或运行 CI，不记录 CI Run、Artifact ID 或 APK hash；本地 APK 不作为 CI 成功证据。

本次修复：评估与情景预览统一校验 scope、现金/持仓总额及唯一有效行情；缺失/重复行情返回 UNKNOWN，不抛空指针或误报区间正常。PC 收益未知时空分段结果显示 UNKNOWN，合法零收益仍可显示“无”；补齐 shared 警告 status 契约并展示 UNKNOWN / NOT_MODELED。公共 README、当前状态与文档索引同步，API 文档补齐 GET preview。

## 功能、接口与安全回归

| 分类 | 能力与证据 | 结论 |
| --- | --- | --- |
| 功能/代码 | `AllocationPolicyServiceTest.java:28` 含区间等号及未舍入边界、收益/分段等号、成本未知、类别/CASH、启停、owner/family 与非法配置；`:63` 补齐错 scope、汇总不一致、缺失/重复行情 | 通过 |
| 接口/权限 | `AllocationPolicyController.java:11` 六个路径；`AllocationPolicyMapper.java:9` 只读写 allocation_policy，查询/更新同时限定 owner AND 当前 family；`AllocationPolicyService.java:27` 家庭管理员校验。`evaluate` POST 无请求体，仅观察，不保存快照 | 通过 |
| 数学情景 | `RebalancePreviewEngineTest.java:15` 多资产目标点/最近边界、等号、亚分舍入、等额反向守恒、CASH/类别、UNKNOWN/STALE 与不一致快照；`:59` 重复预览只读与停用不读财务 | 通过 |
| PC | `web/pc-app/tests/rebalanceCenter.test.mjs:30` 表单/阈值、UNKNOWN/零值、数据时间、显式预览、403 清除证据与重试、metadata create/edit/toggle、安全 API；新增未知分段显示断言 | 通过 |
| Android | `AllocationRepositoryTest.kt:19` MockWebServer 请求白名单、认证、DTO 精度与 null、401/403/503、空态/损坏数据、断网/取消、日期与阈值边界；使用的 ResearchReadState 失败清除数据 | 通过 |
| 脚本/配置 | allocation_policy 初始化和增量结构一致，MySQL JSON 检查及 owner 索引齐全；无自动 migration runner。Gradle 与 workflow 版本同步，无新环境配置键 | 通过，未部署 |
| 依赖/构建 | 既有 Maven/Gradle/npm 模块承载新增代码，shared API/type 均导出；无新增依赖，npm ci 后锁文件未改。APK、后端和全部 web workspace 构建 | 通过 |

财务读取链：`AllocationPolicyController → AllocationPolicyService.evaluate/preview → FinanceRadarService.getRadar / HoldingService.calculateHoldings`；两个入口均 `@Transactional(readOnly=true)`。雷达读取账户/持仓、行情、指标及只读待办/对账；持仓计算只 select 和内存计算。情景引擎为纯函数，规则 create/edit 仅调用 allocation_policy INSERT/UPDATE。不存在自动交易、订单创建、资金/份额占用、结算或正式账本写调用，不调用券商接口。重复评估/预览 verifyNoMoreInteractions(store) 证明仅 scoped detail 读取。既有应用其他页面的人工草稿/结算边界保留，不能将观察页跨研究/风险跳转的 readonly query 当作服务端权限限制。

区间分母是人民币现金 + 持仓、不扣负债；收益为（选中市值 − 成本）/ 成本。止盈仅列已达阈值，不代表卖出。目标/边界情景仅假设金额，不分配其余产品、不校验资金可用性；税、费、滑点、交易限制、外汇未建模，原始价格来源 UNKNOWN。观察与预览分别主动读取，可能来自不同快照；两端显示评估时间/数据日期，不证明行情实时。雷达沿用 3 天行情新鲜度窗口，PC 提醒非当天快照，Android 提醒超过 3 天或未知/未来日期。

PC 原生 label、fieldset、键盘焦点、status/alert 提示与中文重试已静态核对；加载期间禁止重复操作，失败清除观察证据。Android 使用 Compose Material 按钮、polite liveRegion 播报加载/错误；后台不轮询，读取失败清除本次数据，手动重试，不自动重试财务动作。未执行真实浏览器、设备 TalkBack 或真实服务 offline/retry 验收，mock 自动回归不替代这些体验验收。

## 完整性审查与未通过明细

使用 commit-branch-consistency-review 采集依赖范围全部 34 文件；最终 WORKTREE 全量枚举 XML Mapper 20 + 注解 Mapper 3 = 23，SQL 语句 155，Mapper 对象 26 = 脚本覆盖 26 + 无覆盖 0，SQL 脚本 38，未解析/动态表名均 0。完整文件、语句、脚本和逐对象矩阵见 [覆盖附录](mydca_v018_release_coverage_20261002.md)。全量枚举不代表全仓业务语义审核。

| 编号 | 等级 | 文件与位置 | 证据 / 影响 / 最小处理 / 复核 |
| --- | --- | --- | --- |
| DB-001 | 既有警告 | `sql/updatesql/20260610/01_create_draft_ledger_entry.sql:4` | 草稿表通用 init 缺失，仅增量建表。新安装不能只执行通用 DDL；部署方需纳入草稿增量，后续授权范围补齐 init 并在隔离环境复核。本任务不修改范围外 SQL |
| ENV-001 | 待人工验收 | `backend/migrations/20261002_allocation_policy.sql:3` | 仅提交未执行；初始化 `backend/sql/initsql/allocation_policy.sql` 与增量择一，不重复执行。目标 schema 与家庭管理员权限须另行授权环境验证 |
| UX-001 | 待人工验收 | `AllocationScreen.kt:18`、`RebalanceCenter.vue:2` | 真机 TalkBack、真实键盘与登录未测试；需断网→错误→联网人工重试及多页/权限场景验收 |
| CI-001 | 待真实证据 | `.github/workflows/android-test-apk.yml:43` | 命名统一，未运行 CI；只有未来真实成功 run 才回填 artifact/hash |

未修复工程阻断 0；既有警告 1；待验收 3。migration 不是幂等脚本，无自动回滚；本轮未注册自动部署，数据库操作需部署方审核后独立授权。

## 本地验证

| 命令 / 检查 | 真实结果 |
| --- | --- |
| backend `mvn -B test` | 223 项；0 失败/错误/跳过；mock 测试，无数据库 |
| web `node --test pc-app/tests/*.test.mjs` | 23 项通过；配置观察 6 项 |
| web `npx tsc --noEmit -p pc-app/tests/tsconfig.allocation.json` | 配置模型/shared 类型专项通过，不宣称既有全仓 vue-tsc 通过 |
| Android `gradlew.bat testDebugUnitTest assembleDebug lintDebug --no-daemon` | 43 类 / 289 项，0 失败/错误/跳过；APK 成功；lint 0 errors / 2 既有 warnings，含非阻断 information |
| `aapt2 dump badging app-debug.apk` | versionCode 19 / versionName 0.18.0 |
| `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0`（由 cmd /c 启动） | 后端 package、shared / PC / mobile 构建通过；成功仅 stdout/log |
| `git diff --check` | 通过 |

npm ci 准备本地依赖；Android 使用本机 SDK 的进程 ANDROID_HOME，未写 local.properties。首次 PowerShell 直接启动受进程环境错误影响，改用 cmd /c 后执行同一 Windows PowerShell hook 成功。关闭 hook 自动修复轮次，防止自动修改越出 allowed_paths；构建失败由当前任务自行修复。

仅本地提交，不 push。Goal 按 goal_only 由 AiCore/Hermes 汇总企业微信通知，本任务不主动发送消息。
