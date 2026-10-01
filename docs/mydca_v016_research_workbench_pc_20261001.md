# v0.16 PC 研究方案工作台

策略实验室新增研究档案侧栏与详情区；从候选点击「继续研究 · 加入研究方案」，人工填写名称与备注后创建 DRAFT。支持分页、详情刷新、名称/备注/JSON 参数草稿编辑、DRAFT/ACTIVE 切换和二次确认归档。ARCHIVED 终态禁用编辑与运行。

详情展示来源候选、来源 run、strategy/version、原始参数快照、dataset hashes、证据引用、创建时完整证据和后端失效警告。参数草稿独立保存，运行使用后端已经保存的草稿，未保存编辑不进入执行请求。

运行接口仅提交 dataset，不提交策略代码或执行器。后端当前是同步接口，没有队列查询 API；「排队中」明确仅为本地提交状态，「运行中」表示等待响应，不声称取得服务端进度。成功/失败后刷新方案关联历史和主历史。网络中断提示结果可能未知，不自动重试；人工刷新确认后再决定。历史列表按 owner 作用域逐页读取，再按 researchPlanId 筛选，可继续加载更早记录。成功记录选择 2–5 条进入现有 compare/evidence export；失败记录只查看状态与失败码。

页面持续展示「历史回测不代表未来表现」「研究方案不是交易建议」。无交易、订单、结算、账本或券商入口。401/403/409 保留 HTTP 状态，显示登录/权限/并发冲突提示；复用现有登录跳转。无生产数据库连接，未部署 migration、未修改真实财务记录。

## 验证与限制

新增 node:test 测试编译实际 Vue setup 脚本，使用模拟 API 覆盖创建、参数编辑/非法 JSON、归档、排队/运行/成功/失败、重复提交保护、关联历史刷新、空态、权限/登录/冲突、compare/evidence 入口与请求体安全边界。既有 evidence/radar 的旧源码断言对齐当前失败和旧快照展示行为。

验收命令：`node --test web/pc-app/tests/*.test.mjs`、前端 build、`powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1`、`git diff --check`。真实登录后的浏览器体验与目标环境迁移部署仍需人工验收；模拟测试不代表生产可用性。本任务仅本地提交，不推送。

本地结果：PC node:test 10/10 通过；研究工作台专项 vue-tsc 检查通过；完整 hook 后端 package 与 shared/PC/mobile build 通过。额外全量 PC type-check 存在既有记账/订单/持仓页面类型错误，本任务未扩大范围修复，不能声称全仓类型检查通过。
