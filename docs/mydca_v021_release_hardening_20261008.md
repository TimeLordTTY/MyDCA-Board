# v0.21 数据就绪与迁移安全发布硬化

日期：2026-10-08。任务：`task-mydca-v021-release-hardening-20261008`。结论：**工程有条件通过；真实环境可用性待人工验收**。仅本地提交、不 push；未连接数据库、未执行 SQL、未修改真实财务记录、不部署。goal_only 汇总由 AiCore/Hermes 负责，本执行器未发送企业微信消息。

## 审查范围与上游

range：`15199a49f5d7563959fa60c4eccbf697dbf9ebc5..99adee18abc08b79474b5fd3288fb8064d32ac02`，共34个变更文件，完整分类及差异清单见 `v021_review_evidence.json`。工作区初始干净，当前为调度隔离 worktree 的 detached HEAD，未 fetch；本地提交不移动或推送远端 v2。数据库覆盖复核为上游最终树全部 Mapper/SQL，发布硬化没有修改 Mapper 或 SQL。

| 能力 | 上游完整提交 | 配套 |
| --- | --- | --- |
| 离线迁移预检 / DB-001 | 470b54347339035b79f97e8f141723c879f40673 | scripts/migration_preflight.py、完整覆盖矩阵、迁移专项报告 |
| 后端授权诊断 | 46b0fdc491cf23410c17b9064cbb5d14a2d8b412 | DataReadinessController/Service、DTO、DATA_READINESS_API.md、单元测试 |
| Android 个人只读提示 | ca2f9fdd4e44bb14b76cec8a00304bb21a8bbd39 | Retrofit GET、DTO、Repository、状态/卡片、DATA_READINESS_VIEW.md、MockWebServer |
| PC 来源与证据中心 | 99adee18abc08b79474b5fd3288fb8064d32ac02 | shared API/type 导出、导航/router、DataReadiness.vue、模型、SFC/API 回归 |

四个提交均为本地 HEAD 的祖先。这里只证明源码存在与本地验证；外部 Delivery Manifest、上游任务验收收据及五张任务 Goal 完成状态未核验，不声称已完成 Goal 通知。

## 本次修复与交付闭环

1. 迁移预检补齐跨组顺序：基础 DDL → 补充 init/结算字段；草稿主表及强幂等三步骤 → 生命周期；风险规则 → 提醒历史。默认核对清单同步排序。逐项重排反例必须失败，重复路径/脚本仍拒绝。所有检查均只读源码，不执行迁移。
2. Android schema 的汇总与详情都使用固定 UNKNOWN / 未核实部署，即使响应误报 READY 也不显示部署成功；增加状态回归。
3. Android 新增诊断体验形成版本增量：versionName 0.21.0、versionCode 22；workflow 预期 artifact/APK 同步。README、Android README、当前状态与索引同步。

功能、接口、代码、脚本、配置、依赖配套已核对：GET 的 scope/month 和 evidence 字段在 Java、shared、Kotlin 相互对应；PERSONAL/FAMILY 由服务校验，Android 固定 PERSONAL。新功能复用现有授权读取，未增加配置键、依赖、Mapper、数据库对象或调度任务。预检只依赖 Python 标准库。失败返回固定中文诊断，未写原始异常、密码、财务值或来源路径日志；schema/UNKNOWN/来源时间/人工边界有契约注释与文档。

## 跨端回归与安全边界

| 要求 | 实测证据与行为 |
| --- | --- |
| owner/family 隔离 | DataReadinessServiceTest：身份仅来自当前登录用户，家庭管理员校验先于读取；GoalTrackingServiceTest、MonthlyBudgetServiceTest、RiskWatchServiceTest、AllocationPolicyServiceTest 及 ResearchPlanServiceTest 回归既有作用域授权 |
| UNKNOWN/PARTIAL | 空数据、缺成本、家庭覆盖不足、不同预算月、未知进度保留 UNKNOWN/PARTIAL；读取失败 UNAVAILABLE，不当零；schema/汇率/预测覆盖 UNKNOWN |
| 行情时效 | 缺失、超过3个日历日及未来来源不报 READY；保留最早报价/估值/指标日期，需人工核对交易日 |
| 预算期间 | 仅匹配请求月份，不读取其他月份 comparison；month 是统计期间，不是更新时间，也不代表完整现金流覆盖 |
| PC 展示 | 38项完整 PC 回归；缺项 UNKNOWN、部分/失败/陈旧提示、scope/month 清空旧报告、单请求在途、401/403/网络/超时、人工恢复、剪贴板失败与脱敏摘要、GET/SFC/导航 |
| Android 展示 | 299项完整单测；个人 GET/JWT、401/403/503、断网/恢复、非法响应、作用域不符、空证据、旧快照和24小时/未来时间提示；schema 固定未知 |
| 写入隔离 | 诊断不调用风险 evaluate（会保存历史），只读配置 evaluate 无持久化；研究只校验既有证据、不回测。不调用交易、转账、订单、结算、账本 writer；无迁移执行或自动修复入口 |

这些是 mock / 本地测试，不替代真实权限、浏览器/设备体验或数据库语义验证。首50项元数据有覆盖上限，满页不报全量 READY。Android 当前提供个人诊断，家庭管理员诊断在 PC；不会推断家庭完整覆盖。

## 数据库静态完整性

完整逐对象矩阵：`scripts/v021_database_coverage.json`；init/migration 路径、索引引用、前置、重复风险和人工清单：`scripts/v021_migration_preflight.json`。专项报告 `mydca_v021_migration_preflight_20261008.md` 包含 v0.14～v0.19 逐版本路径；v0.15 无新 SQL，需部署方核验历史目录权限/备份。

| 数量闭环 | 数量 |
| --- | ---: |
| XML / 注解 Mapper | 20 / 5 |
| Mapper 总数 = 已解析 + 未解析 | 25 = 25 + 0 |
| SQL语句 / Mapper对象 | 164 / 28 |
| 对象 = 有覆盖 + 无覆盖 | 28 = 28 + 0 |
| SQL脚本 / 涉及对象 | 43 / 66 |
| 新增引用对象 / 动态对象 / 通用初始化缺口 | 0 / 0 / 0 |

DB-001：`sql/initsql/20260610_draft_ledger_entry.sql:4` 与既有更新脚本字节一致，通用初始化缺口已修复；不重写历史 SQL，v0.8 唯一键仍独立人工处理。其余7组路径去注释/空白/IF NOT EXISTS 后结构一致。MySQL对象/索引覆盖不是字段、约束或引擎执行语义证明；IF NOT EXISTS 不验证已有 schema，重复 CREATE/ALTER/index 可能报错，不能把报错当成功。隔离一次性库演练 **NOT_RUN**，目标部署 **待部署方验证**。

## 警告与待确认项

阻断项0；以下2项待确认，限制环境发布声明：

| 编号 / 等级 | 位置与证据 | 影响 / 建议 / 复核 |
| --- | --- | --- |
| DB-ENV / NEEDS_CONFIRMATION | sql/updatesql/20260929/01_settlement_audit_link.sql:2、backend/migrations/20261002_risk_watch.sql:3；仓库有 ALTER/CREATE，无实际 schema 或演练记录 | 不能证明目标环境就绪；部署方逐对象审核列类型、精度、空值、默认值、CHECK/JSON、主键与索引顺序；审核脱敏 schema、脚本hash、执行记录、备份恢复后签署。DB-002～005 明细沿用迁移专项报告 |
| DELIVERY / NEEDS_CONFIRMATION | .github/workflows/android-test-apk.yml:43、android-app/app/build.gradle.kts:14；只有命名约定/本地版本，无v0.21 CI run/artifact/hash或真实设备结果 | owner 后续推送并核验实际 CI 成功，补录 source commit、Run ID、Artifact ID、APK SHA-256；AiCore 关联四上游交付及五任务 Goal 收据。不得把本地 APK 当 CI 交付 |

## 实际验证

- 后端 `mvn test`：265项，失败/错误/跳过均0；不启动真实数据库。
- Web 首次缺 node_modules/tsc；`npm ci` 后 `npm run build:shared` 与 `node --test pc-app/tests/*.test.mjs`：38项通过。
- 离线 `python -m unittest discover -s scripts -p test_migration_preflight.py -v`：6项通过，覆盖双路径、DB-001字节一致、重复/缺步骤/顺序、跨组重排、结构漂移/缺对象、编码与CLI退出码；init 和 migration 路径预检均通过。
- Android：仅进程设置 ANDROID_HOME 指向本机SDK，`gradlew.bat --no-daemon testDebugUnitTest assembleDebug lintDebug` 通过；46类/299项，失败/错误/跳过0；lint 0 errors / 2既有 warnings（DataExtractionRules、ObsoleteSdkInt）。版本/文案修改后重跑，无提交本机SDK配置。
- `cmd /c powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0`：后端package及全部Web构建通过；成功只stdout/log，无声音/弹窗。由当前代理修复，避免自动修复扩大 allowed_paths。
- encoding guard：对本次及四上游变更的全部文本文件进行 Python strict UTF-8 解码、U+FFFD/乱码标记检查；SQL无效编码另有失败回归。`git diff --check` 通过。文档回填后再次执行hook与guard。
- 环境缺项：无专用已确认隔离数据库、真实角色登录、浏览器读屏/键盘及真机测试、v0.21 CI制品、外部Goal收据。未用生产服务补验。

## 人工验收清单

- [ ] 部署管理员确认环境与审批、备份/恢复演练，逐对象比对脱敏 schema；同组 init/migration 只选一条，仅审核缺失脚本，不批量执行历史更新。
- [ ] v0.8先只读重复预检；归一化数据改写须另行授权，再人工加键，不能删重复记录或绕过幂等检查；结算审计字段与v0.16～v0.19对象逐环境确认。
- [ ] 用不同 owner、不同 current-family、普通家庭成员、管理员真实登录，验收允许/拒绝、空数据/部分数据/过期报价、预算跨月与真实来源时间；不执行财务动作。
- [ ] PC真实浏览器键盘/读屏、长内容滚动、切换作用域/月份、断网/超时/会话过期、手动重试、复制权限拒绝；Android真机目标入口、滚动/返回/TalkBack、详情播报、真实断网与恢复。
- [ ] 后续推送后的CI证据：预期 `mydca-android-v0.21.0-<full-sha>` / `MyDCA-Board-v0.21.0-<short-sha>.apk`，实际workflow/run/artifact/hash核验前状态为待推送/待核验。
- [ ] AiCore/Hermes核验五张任务与上游Delivery，按goal_only汇总企业微信；本报告不是通知收据。
