# v0.17 PC 风险观察中心

入口：顶部「风险观察」、财富雷达与研究方案工作台；`/risk-center` 继承登录路由保护。跨页面跳转仅带 `readonly=true`，不携带交易指令。

支持六类规则创建/编辑，个人/家庭作用域、级别、备注、收益方向及小数比例阈值。前端与后端均校验；边界含等号。启停对应 config.muted 持续静默；规则级截止时间使用独立 mute API，可清除。家庭规则由后端管理员权限控制。

当前区域读取各规则最新已保存快照，标明数据日期、评估时间、原因、观察值、阈值、UNKNOWN 与旧日期。仅手工点击「评估当前数据」调用 evaluate；页面加载和历史刷新不评估。未知数据不作为解除，历史状态完全遵从服务端。

历史按规则分页读取，支持继续加载更早数据；规则也支持继续加载。筛选只作用于已加载记录，页面明确提示范围。按 ruleId + fingerprint 去重，保留 OPEN/ACKNOWLEDGED/MUTED/RESOLVED、已读/解除/静默时间。手工已读与静默只写观察元数据。未加载成功的数据不会报为正常空列表，失败可重试。

没有订单、结算、账本或交易 API 依赖。本任务未连接数据库、未修改真实财务记录、未部署；两个后端风险观察 migration 仍需部署方人工部署。真实登录、家庭权限、键盘及目标环境体验仍需人工验收。

验证：PC Node 回归测试（含表单边界、未知/陈旧、fingerprint 去重与状态、API 已读/静默及无交易入口）、前端构建、完整 post-task compile hook、git diff --check。仅本地提交，不推送。

本地 Node 回归 17 项通过。额外运行的全 PC `vue-tsc --noEmit` 未通过：既有 UnifiedEntryModal、Dashboard、Orders 等存在未使用变量和共享类型不一致，Products 还引用未导出的 shared/src 路径；本次新增 RiskCenter、riskWatchModel 无类型诊断。该全量类型检查不属于现有 Vite build/hook；未扩大任务范围修改这些旧问题。
