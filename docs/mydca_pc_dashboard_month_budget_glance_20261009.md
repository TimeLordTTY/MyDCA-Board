# PC 总览本月预算速览交付记录

日期：2026-10-09。任务：task-mydca-pc-dashboard-month-budget-glance-20261009。
基线：v2 上游 db0940e58a9116409a53413259ebd7be184adab1；调度提供的隔离 worktree 为 detached HEAD，本地提交保持此隔离方式，不移动共享 v2 分支、不推送。

## 实现

- Dashboard 仅增加 import 与组件挂载两行；新增 MonthlyBudgetGlance.vue 和纯读取模型。
- 默认本地日历月份、PERSONAL / CNY；进入页面不拉预算，用户点击加载或刷新才调用 budgetApi.list / comparison 的既有 GET。
- 每份独立预算显示计划收入、支出、预留、结余，质量与实际支出、剩余预算、超支状态；不跨预算求和，不把无计划当零。
- 使用 completeActual：仅质量 OK、所有实际字段为有限数字、overspent 为布尔时显示完整实际结论。合法零显示0.00；PARTIAL、UNKNOWN、UNAVAILABLE 或缺失字段保持未知。
- 扫描最多5页 / 100份、对比最多20份、并发最多3个、总加载超时30秒。未证明列表穷尽或对比超限时清空结果并提示“无法证明本月全量”。
- 列表按月份、作用域、币种筛选；校验重复 ID 配置及 comparison.budgetId。既有 comparison 契约不包含月份、作用域、币种，因此以已授权列表配置绑定口径，并拒绝返回值中的可选冲突元数据；不能证明跨请求原子快照。
- 筛选、账号、Token、家庭/角色对象、路由变化、卸载与 keep-alive 停用、取消、失败均清空并中止请求；generation 防止晚到结果回填。超时支持人工重试。
- “查看完整预算”复用 GoalBudgetCenter 路由；不新增编辑、复制或资金动作。家庭权限交由后端 JWT，不在客户端推断权限。
- 标明金额来源、客户端读取时间、家庭授权口径及预留仅为计划；没有敏感数据持久化。

## 本地验证

| 检查 | 结果 |
| --- | --- |
| `node --test web/pc-app/tests/*.test.mjs` | 70/70通过（既有60 + 新增10） |
| `node web/node_modules/vue-tsc/bin/vue-tsc.js --noEmit -p web/pc-app/tests/tsconfig.monthlyBudgetGlance.json` | 通过，无诊断 |
| 全量 PC vue-tsc 对照 | 基线99条、当前99条；去除行列位置后诊断内容完全一致。全量类型检查仍失败，不宣称全量通过 |
| `npm run build:shared`（web目录） | 通过 |
| `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0` | 通过，后端 Maven skipTests 打包、shared/PC/mobile 构建成功；没有运行后端测试或连接数据库 |
| `git diff --check` | 通过 |

新增脱敏 Node 夹具覆盖跨年、闰年、本地月份、独立多预算、无计划、作用域/币种/月筛选、分页及对比上限、并发、实际零/null、各不完整质量、ID/可选元数据冲突、401/403与网络失败重试、取消/超时/身份和路由变化、卸载/停用、晚到旧请求、路由和无障碍标签、无写入/余额重算/持久化。测试运行 Vue 编译后的 setup 和纯模型，不替代真实浏览器验收。

全量对照工具 `tests/typecheckMonthlyBudgetGlance.cjs` 在 MONTH_GLANCE_BASELINE=1 时只通过内存读取任务起点 HEAD 的 Dashboard，未改写基线文件；当前新增组件也参与检查。复核基线时应在提交前运行，或将工具中的 HEAD 指向上述基线提交。

## 人工验收清单与边界

- 在目标环境确认 monthly_budget 相关 schema 和既有 GET 正常；本任务不执行 migration，不声称已部署可用。
- 真实个人、家庭管理员、普通家庭成员分别登录，确认后端授权/拒绝及账号、家庭、角色变化清空结果。
- 浏览器确认进入首页不请求预算；点击后仅预算 GET，其他首页刷新不触发此卡片；原订单/结算行为未改变。
- 键盘/读屏验证筛选标签、加载与错误播报、取消按钮、完整预算链接；窄屏、主题、真实断网及30秒超时重试验收。
- 用脱敏目标环境数据复核多计划不求和、无计划未知、0/null/不完整质量及预留文案。

未连接生产数据库、未修改财务记录、未交易、未部署、未推送、未修改 inbox。任务交付通知由 AiCore/Hermes independent 企业微信流程负责；本任务不发送外部消息，也不声称通知已送达。
