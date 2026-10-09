# PC 今日待办诚实状态交付（2026-10-09）

## 实际修改与口径

- Dashboard 今日待办明确分为未加载、加载中、成功、失败。初始化/刷新立即清除旧快照；请求失败或契约校验失败显示中文错误、草稿数量“未知”和“手动重试”，不将失败转换成0。
- 成功0草稿显示“当前已加载的草稿待办为0”；总数标注“本接口”，展示服务端日期，明确与“今天建议”和独立“待结算清单”口径分开，不合计或推断账户状态。
- 待结算、策略建议固定显示“未接入 / 未统计”。当前 shared 类型与 TodoService 没有字段级接入证据；若未来出现非零相关指标，本轮客户端会拒绝未知契约，需另行明确契约才能展示。
- 纯模型校验合法日期、非负安全整数、当前类型与合计关系、明细结构/唯一ID、字符串字段。当前后端只返回 DRAFT 且明细最多20条，按 `min(draftCount, 20)` 校验；计数/列表并发不一致时失败，不猜测为0。
- 请求 generation 与身份/本地日期检查丢弃旧响应；用户或权限资料/token 变化同步清除快照，卸载/停用阻止迟到初始化请求，重新激活只恢复人工加载能力。成功快照到本地午夜失效，不自动新增读请求。
- 标题和摘要仍使用 Vue 文本插值，类型使用固定中文标签；跳转忽略后端自由 actionPath，仅将 DRAFT 正整数ID传入现有 DraftInbox 命名路由。没有自动确认或自动结算入口。

## 安全边界

只修改 PC 代码、测试和本文档。未修改 shared/后端/Android/SQL/调度器，未访问生产数据库、券商接口或真实财务记录。独立待结算模板与已有人工 preview/二次确认/confirm 代码通过基线比较保持原样。新增模型不记录API响应、错误对象、财务数据或身份凭据；身份只在内存中用于响应有效性检查。

## 自动验证

- `node --test web/pc-app/tests/*.test.mjs`：80/80通过（既有70项 + 新增10项）。覆盖初始化/加载、成功0/N/20条上限、401/403/500/offline及重试、缺字段/null/NaN/负数/类型错误/重复ID/不一致、刷新竞争与晚到失败、账号变化、跨日、停用/卸载、安全路由、原有结算代码边界。
- `node web/node_modules/vue-tsc/bin/vue-tsc.js --noEmit -p web/pc-app/tests/tsconfig.todayTodo.json`：局部模型类型检查通过。
- 全量PC vue-tsc：任务前后均99条历史诊断。使用既有 `tests/typecheckMonthlyBudgetGlance.cjs` 与 `MONTH_GLANCE_BASELINE=1` 读取 HEAD 的 Dashboard；忽略因新增代码产生的行列偏移后诊断集合相同。**全量类型检查未通过**，未扩大范围修复历史问题。依赖安装但 shared 未构建时曾有298条诊断，该结果不作为有效基线。
- `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0`：完整后端打包与前端 shared/PC/mobile 构建通过；后端 hook 使用 skipTests，不声称运行后端单测。成功仅stdout/log，无Beep或弹窗。
- `git diff --check`：通过。
- 本工作树原先无node_modules，使用 `npm install --package-lock=false` 安装本地构建依赖，未修改依赖清单或锁文件。

## 仍待验证与交付边界

尚未进行真实浏览器/授权账号验收：需要核验实际登录切换、网络断开与鉴权失败、快速刷新、后台休眠跨午夜、中文状态可读性、草稿详情跳转与现有人工结算交互。浏览器后台暂停可能延迟本地午夜定时器，应在真实浏览器验证恢复时的显示。

仅本地提交，不推送、不部署。当前会话未提供 AiCore Delivery 或 Hermes 企业微信交付工具；本地测试/构建证据不能代替外部 Delivery/通知回执，相关回执仍待既有调度交付链生成，不能声称已通知主人。未修改 agent-inbox，未创建业务task/goal。
