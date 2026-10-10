# 首页资产与预算友好文案交付记录

- 任务：`task-aiw-r1-02-friendly-kpi-budget-20261010`
- 日期：2026-10-10；仅本地提交，不推送、不部署。
- 开始时 `git fetch origin main v2` 成功。HEAD 与 origin/v2 均为 `cd89f6a62e596f090f47a25715f9a51aefd4671c`；origin/main 为 `b9560d35f4458c8b4c7fbd8ecba390b66f216821`。
- 前置就绪文案提交 cd89f6a 已包含；目标速览 cbab2c5、资产真值模型 9d93f00 已存在，本任务在这些实现上调整展示。

## 展示映射

| 内部状态 | 用户看到的含义 |
| --- | --- |
| KPI complete（包括真实0、负数） | 只展示格式化金额，不重复“输入完整”徽标 |
| KPI partial | “已读到 金额 · 还有数据缺失”，不作为完整余额 |
| KPI unavailable / null | “暂无法确认 · 数据未读全”，不变成0 |
| KPI loading | “正在读取…” |
| 预算未手动读取 | “还未读取本月预算”，给出按钮操作说明 |
| 预算成功读取且当前作用域/币种列表为空 | “还没有本月预算”，突出“前往目标与预算创建”；无计划不等于0元或无支出 |
| 预算401 | 登录已失效，重新登录后手动重试 |
| 预算403 | 没有读取此预算的权限，不推断余额与流水 |
| 预算读取失败、扫描上限、响应不一致 | 明确读取失败与手动重试，保留原读取保护错误原因，不显示无计划 |
| 预算OK且 completeActual 通过 | 按本预算分类核对流水；实际支出/剩余预算保持真实金额，包括0 |
| 预算PARTIAL | 部分流水未读全，剩余预算和超支暂无法确认 |
| 预算UNKNOWN/UNAVAILABLE/OK但实际字段不完整 | 实际流水暂无法确认，不能按0支出计算 |
| 目标OK且 completeGoalProgress 通过 | 根据本次读取的资产计算进度 |
| 目标PARTIAL | 仍有资产数据缺失，显示已读到的部分金额，不声称完成率或达标 |
| 目标UNKNOWN或进度契约不完整 | 暂无法确认完整资产进度 |

Dashboard 增加原生 details/summary 金额解释与键盘焦点样式、金额区域 aria-busy。预算和目标继续使用原生按钮、带 label 的选择器、focus-visible、status/alert、aria-busy。预算空态使用现有 GoalBudgetCenter 路由链接，不触发创建或财务写入。

## 验证证据

- 锁定依赖安装：`npm ci --ignore-scripts --no-audit --no-fund`（web）。
- `npm run build:shared`（web）通过后运行 `node --test web/pc-app/tests/*.test.mjs`：130通过，0失败。
- 模型测试覆盖真实0/负数、缺数据、完整实际不足、401/403；组件状态测试覆盖手动读取、取消、身份/作用域/币种变化、超时与旧请求失效。
- 新增预算 Vue 模板在自定义 Vue renderer 中渲染，覆盖未读取、成功空列表、403、网络错误、真实0、PARTIAL，以及创建链接。select DOM 指令在文字渲染测试中替换为空；不声称完成真实浏览器键盘或屏幕阅读器人工验收。
- `node web/node_modules/vue-tsc/bin/vue-tsc.js --noEmit -p web/pc-app/tests/tsconfig.monthlyBudgetGlance.json`：通过。
- 同入口 `tsconfig.goalProgressGlance.json`：通过。
- `node web/node_modules/typescript/bin/tsc --noEmit -p web/pc-app/tests/tsconfig.dashboardKpiTruth.json`：通过。
- `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0`：通过，后端 mvn -DskipTests package 与 shared/PC/mobile 构建通过。后端测试未运行；本任务未修改后端。
- `git diff --check`：通过。
- 首次测试因未安装依赖、未构建 shared/dist 失败；准备依赖和共享产物后重跑通过。新增渲染测试适配完成后全量测试通过。

## 本地 Delivery 证据与边界

```json
{
  "task_id": "task-aiw-r1-02-friendly-kpi-budget-20261010",
  "delivery_scope": "local_commit",
  "source_commit": "containing Git commit; resolve with git log -1 --format=%H -- docs/mydca_user_friendly_kpi_budget_copy_20261010.md",
  "tests": { "pc_node_tests": { "passed": 130, "failed": 0 }, "targeted_types": "PASS", "compile_hook": "PASS", "diff_check": "PASS" },
  "push": "NOT_RUN",
  "production_access": "NOT_RUN",
  "external_aicore_delivery_manifest": "NOT_VERIFIED",
  "hermes_owner_notification": "NOT_SENT"
}
```

此记录提供 AiCore 收集本地提交、测试及构建结果的证据；当前未读取或修改外部 Delivery Manifest、不声称目标已由 AiCore/Hermes 验收。仅修改 allowed_paths 内的前端及本报告，未改任务文件、调度、审批。金额计算、财务接口与读写调用链、权限隔离、分页/并发/超时边界均保持原实现；无新增自动请求或轮询，不连接数据库、不修改财务记录、不执行交易。
