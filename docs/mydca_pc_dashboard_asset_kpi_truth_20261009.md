# PC 首页资产 KPI 可信显示（2026-10-09）

本任务仅修改 `web/pc-app/**` 与本报告。基线提交为 `89a685b525f75f0a294104a14a0dcc8ed19eaf7b`。未修改后端/shared 源码、资金数据、交易、调度器或 inbox；未连接任何数据库、券商或生产环境，未部署、未推送。

## 显示口径

- 净资产：仅本轮账户读取成功、概览现金/负债为有限 number、持仓估值完整时显示现金＋持仓市值－负债；缺必要来源即未知，不显示部分净资产为最终资产。
- 可用资金、占用、生活费：复制本次成功查询的现金叶子账户，继续复用 REAL 与 CASH/BANK/PAYMENT/MMF/BROKER 筛选和 SPENDABLE 语义；校验余额/占用，负余额保留。成功查询无账户可显示真实零；失败与非法字段不当零。
- 持仓市值：成功读取持仓后，以有效份额乘对应场内报价或场外净值。合法零份额不需行情；空持仓还需账户/概览来源佐证。部分报价显示“已知部分”，无报价显示未知。
- 总/场内/场外盈亏：只聚合价格、份额、成本均有效的对应部分；成本缺失、渠道无法识别不冒充完整收益。今日净损益/月净流入分别校验可选概览字段，缺失为未知。
- loading/complete/partial/unavailable 分别显示“读取中·未知”“输入完整”“已知部分/部分已知”“未知·来源未完整取得”。顶部不再用旧默认零计算或收益颜色诱导判断。

“输入完整”只描述本轮数学输入可用，不能代表覆盖全部资产、授权范围、实时行情或全系统资产准确。跨接口非原子；显示本地读取完成时间与“口径未完全核实”，不推断行情时效。

## 来源核查与请求边界

`HoldingService.java:340` 附近使用 latestNav 计算 marketValue，缺 latestNav 时 marketValue/unrealizedPnl 返回零，HoldingInfo 未提供估值来源时间。因此本次不将 marketValue 当作行情缺失的替代，不把无行情解释为清仓或亏光。

仅复用账户、概览、持仓、行情、净值只读 API；不增加后端资金接口、轮询、持久化。请求局部快照与代次/身份校验阻止旧请求覆盖；账户 Store 读取在本页面内串行并立即复制，避免重复刷新相互污染。token/用户（含 familyId）变化、跨标签 storage token 变化、unmount/deactivation 清空 KPI；activation 只恢复人工读取能力。403 清除存储 token 后也拒绝旧结果。API 请求本身沿用共享 HTTP 超时，失效请求不取消传输，但不能发布为当前 KPI。

待办、待结算和预算质量独立，不进入资产状态。既有待结算读取仍先于资产加载；结算 handler/freshPreviewToken 代码保持原样。删除仅因替换顶部展示而失去用途的旧 computed，不改现有持仓列表/配比数学口径。本次不修复这些非顶部展示区域的历史质量问题。

## 离线验证

- `node --test web/pc-app/tests/*.test.mjs`：110/110 通过（原87项＋新增23项）。覆盖首屏、成功零/空账户、负余额、来源失败、非法金额/份额/价格/成本、无/部分/全部行情、网络/403、重复刷新、旧成功/旧失败、身份撤销、停用与人工重试；现有待办、结算和预算回归通过。
- `node web/node_modules/typescript/bin/tsc --noEmit -p web/pc-app/tests/tsconfig.dashboardKpiTruth.json`：通过。
- 全量 vue-tsc 按同依赖/已构建 shared 对比基线：使用已有 `tests/typecheckMonthlyBudgetGlance.cjs` 的 `MONTH_GLANCE_BASELINE=1` 从 HEAD 读取任务开始 Dashboard，再运行当前版本；将行列位置归一后比对诊断。历史99项保留，不宣称全量通过；本次新增诊断须为0。
- `npm run build:shared`：通过。PC production build 由下述 hook 执行。
- `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0`：backend package、shared/PC/mobile build 通过，成功仅 stdout/log，无 beep/toast。
- `git diff --check`：通过。

最初工作树没有安装依赖与 shared dist，测试因模块缺失无法运行；通过 npm ci（不更改依赖清单）与 shared build 后重跑，以上为有效结果。构建会产生忽略的 node_modules/dist/target/log 工件，不纳入提交。

## 人工与交付边界

真实浏览器布局/键盘/读屏、真实登录及权限403、账户/家庭切换、目标数据库中的实际接口字段和行情时间语义仍待人工验收。离线模型和模板接线测试不是浏览器交互测试，不冒充生产可用性或数据库验收。

本任务按当前指令仅本地提交，不 push。AiCore 导入到 MyDCA-Board@v2、Hermes 企业微信送达及外部 Delivery manifest 由既有交付流程负责；Codex 本轮没有调用发送消息工具，未核验外部送达，不伪造 receipt/manifest，不创建新业务任务。
