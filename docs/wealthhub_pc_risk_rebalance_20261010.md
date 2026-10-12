# 财富中枢 PC 风险与配置观察：独立重建

任务task-aiw-r2-17-risk-rebalance-20261010；2026-10-12。
基线为正式R2-16 b9fd98c5d9c1d817e1a2ec3858b88cb594185b3b。原candidate c704e124df029f32f8a92e9bf679a5a848cb0876只提供经逐文件审查的PC内容；原开发期失败、收尾失败、日志、截图、类型比较和临时preview脚本不作为新证据。原失败原件不改。

## 业务契约

风险页保留规则元数据、人工只读评估、快照/提醒分级与历史、ACK/静默。UNKNOWN和STALE均不能视为正常或解除；来源指纹、数据时间/评估时间分别显示。配置页保留规则元数据与原readonly评估/预览；下限≤目标≤上限、真实零/未知、资金调整正负相抵、未建模税费/滑点/交易限制/外汇和资金可用性保留。失真非finite或不守恒情景不展示假设权重。来源持仓导航为readonly，不建单/交易/划款/持仓配平。家庭权限仍由原后端控制。

## 新验收

提交只描述内容，不自称已验收。AiCore按原approval/allowlist/依赖与锁创建新单提交、新基线、新日志，执行全PC Node、所有既有局部TS及新增严格risk配置、全量类型诊断多重集合、原完整hook MaxFixRounds0、Edge隔离mock浏览器。原新增的历史证据文件从新树剥离，不将旧PASS带入。
浏览器只使用3115无代理静态preview及假凭据，全部业务HTTP mock；风险零/未知/STALE、历史状态/ACK/静默、失败重试/403家庭、持仓导航、配置上下限、只读评估/预览/守恒/缺口、1440/1100/760/390、深色/键盘全新执行。新原始回执须echo当前fixture并显式PASS/完整隔离边界，pageerrors/unknown/financialWrites为0。

正式Delivery仅经原Runtime.integrate/Finalizer，原Goal依赖不改。完整证据在AiCore reports/work-rescue/20261012-r215-independent-rebuild.md及附件。生产数据库、真实财务记录、正式登录凭据、交易/结算/入账、部署禁止。完整PC历史类型错误不宣称通过；hook的mvn -DskipTests package不代表后端单元/数据库测试；mock不替代真实角色/业务端到端。
