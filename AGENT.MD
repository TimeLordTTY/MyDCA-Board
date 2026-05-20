# Agent Guide

本文件用于指导 AI 编码代理在本仓库中工作。若本文件与用户当前指令冲突，以用户当前指令为准；若与 `AGENTS.md` 的构建收尾要求冲突，以 `AGENTS.md` 和全局 Task Completion Hook 为准。

## 项目概览

- 项目名称：Wealth Hub / MyDCA-Board，个人或家庭财富管理平台。
- 后端：`backend/`，Java 17 + Spring Boot 3.2 + MyBatis XML Mapper + MySQL 8 + JWT。
- 前端：`web/`，npm workspaces 管理 `shared/`、`pc-app/`、`mobile-app/`。
- PC 端：`web/pc-app/`，Vue 3 + TypeScript + Element Plus。
- 移动端：`web/mobile-app/`，Vue 3 + TypeScript + Vant。
- 共享层：`web/shared/`，封装 API、类型、常量、Pinia store 和通用工具。
- 脚本：`scripts/`，包含行情采集、指标计算、调度、货币基金回填等 Python/Java 工具。
- 数据库脚本：`sql/initsql/` 放初始化脚本，`sql/updatesql/YYYYMMDD/` 放增量变更脚本。

## 工作原则

- 默认使用中文回复用户；新增或修改代码注释时也使用中文，除非局部文件风格明确要求英文。
- 改动要小而聚焦，只处理用户要求的范围，不顺手重构无关模块。
- 先阅读现有实现和命名，再按项目已有 controller、service、mapper、model、Vue view/component、shared API/type/store 的分层继续写。
- 仓库可能已有用户改动；不要回滚、覆盖或格式化与任务无关的文件。
- 涉及资金、份额、净值、费率、流水、持仓等逻辑时，优先确认精度、入账方向、历史数据兼容和 SQL 迁移影响。
- 涉及前后端契约时，同时检查后端接口、前端 shared API、类型定义和调用页面，避免只改一端。

## 常用命令

以下命令说明了用途；执行前按任务需要选择最小验证范围。

- `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1`
  - 任务完成后必须执行的总验证 hook，会编译后端和前端，并保留成功提示音与系统通知。
- `cd backend; mvn -DskipTests package`
  - 编译后端 Spring Boot 项目，不运行测试。
- `cd web; npm install`
  - 在前端 workspace 根目录安装依赖。不要在 `web/shared/`、`web/pc-app/`、`web/mobile-app/` 中分别安装依赖。
- `cd web; npm run build`
  - 构建 shared、PC 端和移动端。
- `start-dev.bat`
  - 本地开发启动脚本，会构建 shared 并启动 PC 与移动端开发服务器。
- `run-backend.bat`
  - 本地启动后端服务。

## 强制收尾检查

任何代码或项目文件编辑后，最终回复用户前必须在仓库根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1
```

如果 hook 编译失败，继续修复并重新运行，直到通过或用户明确要求停止。不要关闭或移除 hook 中的成功提示音和系统通知。

## 后端约定

- Java 包根为 `com.timelordtty.dca`。
- 常见分层：
  - `controller/`：REST 接口。
  - `service/`：业务逻辑和事务边界。
  - `mapper/`：MyBatis Mapper 接口。
  - `model/`：数据库实体。
  - `dto/`：请求或响应对象。
  - `resources/mapper/*.xml`：SQL 映射。
- 新增数据库字段时，通常需要同步更新 model、mapper XML、必要的 service/controller、前端类型和 SQL 变更脚本。
- MyBatis XML 中保持字段名、别名、resultMap 与 Java 属性一致。
- 不要把真实密钥、账号、生产数据库密码写入配置或文档。

## 前端约定

- 依赖统一由 `web/package.json` 的 npm workspaces 管理。
- `@wealth-hub/shared` 是共享库，应用端通过 workspace 引用。
- `web/shared/package.json` 中 `vue`、`pinia` 应保持在 `peerDependencies` 和 `devDependencies`，不要移入 `dependencies`。
- `web/shared/vite.config.ts` 的 external 应避免把 `vue`、`pinia` 等应用运行时实例打进共享包。
- 应用端 Vite 配置应保持 Vue、Pinia、Vue Router 去重，避免多个运行时实例导致 Pinia `_s` 相关问题。
- PC 端优先沿用 Element Plus 组件和现有页面结构；移动端优先沿用 Vant 和现有移动布局。
- 修改接口调用时，优先在 `web/shared/src/api/` 和 `web/shared/src/types/` 中维护统一契约。

## SQL 与数据迁移

- 初始化结构放 `sql/initsql/`。
- 增量脚本按日期目录放在 `sql/updatesql/YYYYMMDD/`，文件名使用有序前缀，例如 `01_add_xxx.sql`。
- SQL 变更要考虑历史数据、默认值、非空约束、精度和回填逻辑。
- 涉及生产数据修复的脚本应尽量可重复审阅，避免隐式依赖本地临时状态。

## Python 与调度脚本

- `scripts/market/` 负责基金、ETF 等行情采集。
- `scripts/indicator/` 负责 MA、MACD、RSI 等指标计算。
- `scripts/scheduler/` 负责调度任务。
- 修改脚本时同步检查对应 `requirements.txt`、README 和配置读取方式。

## 验证策略

- 小改动至少运行相关编译或类型检查；任务结束必须运行 post-task compile hook。
- 后端接口或 SQL 变更优先用 Maven 编译验证。
- 前端视图、API、类型变更优先用 `cd web; npm run build` 验证。
- 跨端或跨层改动要检查后端接口、shared 类型/API、PC 页面和移动页面是否一致。
