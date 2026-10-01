# v0.16 研究闭环发布硬化

- 任务：`task-mydca-v016-release-hardening-20261001`；本地验证日期：2026-10-02 +08:00。
- 验证基线：`6a6765ed3e9ee6e9e4cc94de6211dde8bd2b1310`，包含后端方案、受控回测、PC 工作台与 Android 只读查看四项交付。本任务只本地提交，不推送、不部署。

## 发布能力与修复

研究方案复用 owner/user + family 双隔离，来源证据快照与参数草稿分离；支持 DRAFT/ACTIVE 切换和不可逆 ARCHIVED。PC 人工创建、编辑、归档与显式受控回测，运行使用后端保存的策略/version/草稿及白名单离线引擎。Android 仅分页查看方案、证据和关联历史。历史研究不代表未来表现。

PC 刷新方案列表失败时清空旧详情、关联运行与对比选择，避免断网后继续编辑旧快照；保留可重试错误。404 给出中文不存在/权限提示，英文网络错误统一中文。新增实际 Vue setup 回归验证断网清理和恢复。

Android 发布版本收敛至 `0.16.0 / versionCode 17`。workflow 预期 artifact 为 `mydca-android-v0.16.0-<full-sha>`，APK 为 `MyDCA-Board-v0.16.0-<short-sha>.apk`。没有真实 CI run/artifact/hash，本地 APK 不冒充 CI 证据。历史专项中 0.15.0 / 16 是当时增量验证版本。

## 回归与边界证据

| 范围 | 实际验证 |
| --- | --- |
| 方案生命周期与隔离 | `ResearchPlanServiceTest`：创建快照、独立参数、状态/归档、并发冲突、owner/family 越权、数据和来源失效、GET 无写入、4 条 MyBatis SQL 参数绑定 |
| 受控回测与历史 | `ResearchPlanBacktestServiceTest`：独立运行 ID、缓存命中、重启读取、数据 hash、非法代码/版本/参数、缺失数据和执行失败、归档拒绝 |
| v0.15 compare/evidence | 既有 compare/research/evidence 测试；新增解压 ZIP 校验 manifest/compare 的方案关联，移除历史文件 researchPlanId 模拟 v0.15 文件并验证读取、compare、export 兼容 |
| PC | 实际 Vue setup 模拟 API：创建/编辑/归档、重复运行保护、关联历史、权限/登录/冲突、空态、错误、断网刷新及手动恢复、dataset-only 请求；compare/evidence 入口复用 |
| Android | MockWebServer：GET 路径/空请求体/认证、401/403/404/503、nullable 指标、失败运行、关联隔离、加载/空态/错误/重试状态、DRAFT/ACTIVE 过滤 |
| 中文与 accessibility | PC 表单 label、原生按钮、aria-labelledby/aria-pressed、alert/status、focus-visible；Android 使用 Material 文本按钮、可滚动列表、中文加载/失败/未知文案和系统返回。静态复核与 lint 已执行，实际键盘/读屏/大字体仍须人工验收 |
| offline/retry | PC 运行网络中断明确结果可能未知，不自动再次发起；Android 读取失败清除旧数据并提供“刷新 / 重试”，不轮询、不发起运行 |

安全链复核：研究 Controller → ResearchPlanService/ResearchPlanBacktestService → ResearchPlanMapper/离线 BacktestLabService；4 条方案 Mapper SQL 仅引用 `research_plan`，无财务表。PC 工作台调用 researchPlanApi/backtestApi，Android 研究入口只声明 GET。未发现研究 UI/API 的隐式订单、结算、正式账本或券商写入口；既有人工记账确认边界保持独立。

## 本地验证

| 命令 | 结果 |
| --- | --- |
| `mvn -f backend/pom.xml test` | 27 个测试类、206 项，0 失败/错误/跳过 |
| `python -m unittest core.backtest.test_engine` | 5 项通过 |
| `node --test web/pc-app/tests/*.test.mjs` | 11 项通过；首次因工作树未安装依赖/未构建 shared 失败，npm ci + shared build 后重跑通过 |
| `node web/node_modules/vue-tsc/bin/vue-tsc.js --noEmit -p web/pc-app/tests/tsconfig.research.json` | 研究工作台专项类型检查通过 |
| `android-app/gradlew.bat testDebugUnitTest assembleDebug lintDebug --no-daemon`（工作目录 android-app） | 41 个测试类、281 项，0 失败/错误/跳过；APK 构建通过，lint 0 errors / 2 既有 warnings |
| `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0`（cmd 启动） | 后端 package、shared/PC/mobile build 通过；成功仅 stdout/log |
| `git diff --check` | 通过 |

使用 MaxFixRounds 0 将失败留给当前执行器修复，避免 hook 启动另一代理绕过 allowed_paths；完整构建范围未缩减。未声称全仓 PC type-check 通过：此前专项已记录其他页面既有类型问题，本次执行研究专项类型检查和全前端生产构建。

## 部署与交付限制

`backend/migrations/20261001_research_plan.sql` 与 `backend/sql/initsql/research_plan.sql` 提供同一研究表结构，升级/新安装择一使用；不接自动 migration runner，本任务未执行。v0.8/v0.14 历史 migration 的目标部署状态也未验证。部署方须人工审核、备份、部署并验收目标 schema；历史目录须配置持久化与权限。

数据库集成、真实登录后的 PC/Android 生命周期/断网/TalkBack/键盘/大字体体验仍待人工验收。mock/临时文件测试不等于目标环境可用；未连接生产数据库、修改财务记录、交易或调用券商 API。

通知策略为 `goal_only`：任务结果供 AiCore/Hermes 汇总到 Goal 后发送企业微信通知。本执行器未发送任务级通知，未宣称 Goal 回执已取得；CI 证据待主人后续推送并真实构建成功后回填。
