# 财富中枢 PC 年轻个性版：全站回归与发布准备

- 任务：`task-aiw-r2-20-ui-release-regression-20261010`；最终核验日期：2026-10-13，Asia/Hong_Kong。
- **本源码为同任务受控独立重建内容；生产发布不通过。正式工程完成以新鲜固定验收及 AiCore Delivery 为准。** 后端订单授权缺口、真实角色验收、owner 设计确认及外部 Delivery/Goal 收据仍是发布门禁。本报告不授权上线。
- 当前输入 HEAD / 控制器基线：`33bdb3b916b8a344966742ebba280752a5dd0bfc`。旧来源候选为 `0ab91bde52262798feefec5415d2fe525b58ede9`，旧run因item_41预览端口占用而failed；本次只复用逐文件审查的内容，不继承旧执行回执或成功声明。没有 fetch、push、部署、迁移、数据库连接或真实财务操作。
- 本任务只修改 PC 测试/证据与三个获准文档；`web/pc-app/src`、backend、shared、SQL、Android、依赖清单/锁、构建入口均保持输入版本。没有覆盖旧源码、切换/reset/restore 源码、编辑控制器隔离树或重跑旧版本基线。
- 旧版行为依据 R2-06 [全路由/操作契约](wealthhub_pc_young_style_contract_20261010.md) 与只读 Git diff；类型比较仅使用本次控制器报告，历史专项文档的计数和旧 PASS 不充当新证据。

## 提交与全量来源

14 个依赖提交均已在输入 HEAD，顺序及完整文件清单见 [交付/源码/调用清单](../web/pc-app/delivery/r2-20-inventory.json)。R2-06→R2-19 共 128 个变更文件；受保护目录变更 0。当前 PC 源码清单 74 文件，API/store 调用位置 195，shared API 的字面路径定义 115；清单记录文件、行号、方法和路径。数字是源码枚举，不表示每个后端路径都经过真实 HTTP 验收。

| 交付 | 本地提交 | 本轮对应测试/证据 |
| --- | --- | --- |
| R2-06 契约映射 | `c7dcdfb236a8cc42a33f201ed705c704c51a17ae` | 本文逐页及按钮门禁对照，原报告保留 |
| R2-07 外观基础 | `12636c1e16770847fc2c40868a1307ba49a7618e` | foundation Node + browser，18组主题/配色/宽度 |
| R2-08 导航壳 | `e88c3cd43833fd0dadc87224550925f0e8ae1d0f` | navigationShell Node + navigation browser |
| R2-09 首页 | `83a3fb062edbdd01b96cc82a26ab2f63145bde11` | dashboardHome/KPI/雷达/待办/预算/目标 Node；portalAuth 全页浏览器 |
| R2-10 账户/产品 | `a01a4b64112c866030bed711180594cfbcd9caa1` | accountsProducts Node + browser |
| R2-11 持仓 | `32214f68dcc82ace10d845457cc1fccb7242d6e5` | holdingsExperience Node + browser |
| R2-12 流水/记账 | `8f18d6a726bab65db60dbafbbcbee7ecbacef623` | ledgerExperience Node + browser |
| R2-13 草稿 | `b9d2d4453737e098d9162be44b3004e6f58cc65f` | draftWorkflow Node + browser |
| R2-14 订单/结算 | `a9fdb126a2379e9215cf4f9606cbae69894d068b` | ordersSettlements/pendingSettlementList Node + browser |
| R2-15 目标/预算/情景 | `97bef8a477e353b9227dec75b9cfb0c2e6cfa9de` | goalBudget/goalForecast/budgetReview 等 Node + goalsPlanning browser |
| R2-16 研究 | `b9fd98c5d9c1d817e1a2ec3858b88cb594185b3b` | strategyLab/researchWorkbench/evidenceExport Node + browser |
| R2-17 风险/配置 | `200feb0acb4c173f482c1daa66952315d3ee925b` | riskCenter/rebalanceCenter Node + riskRebalance browser |
| R2-18 就绪/设置 | `948de37dfbe4489ad3e3c52bac1b8a7b95d0120b` | dataReadiness/readinessSettings Node + browser |
| R2-19 门户/认证/无障碍 | `33bdb3b916b8a344966742ebba280752a5dd0bfc` | portalAuth Node + browser，双认证边界和跨页矩阵 |

这些提交的本地存在不证明 AiCore Runtime.integrate、正式 Delivery Manifest 或 Hermes 企业微信汇总已成功；外部交付状态 **NOT_VERIFIED**。R2-20 的最终本地 commit 以本报告所在提交及控制器提交回执为准，不在提交内自填循环哈希。

## 完整路由与操作矩阵

以下路径加 `/api/v2`；页面部署基路径仍为 `/wealth-hub/`。原 R2-06 的 17 个具名页面、14 个业务子页和 `/` 容器全部保留；补齐待结算导航。B=本轮真实 Edge + mock 浏览器，N=本轮 Node mock/SFC 回归，S=源码与原契约核对。**S/N/B 均不能替代真实后端权限验证。**

| 路由/页面 | 操作与现有 shared/后端路径 | 旧版→当前结果及证据 |
| --- | --- | --- |
| `/` | 认证后重定向 `/dashboard` | 基路径/认证守卫保留；S、navigation N/B |
| `/dashboard` | 资产：GET accounts、dashboard/asset-overview、holdings、market/quotes、nav/latest | 保留现金/占用/负债和旧 KPI 口径；未知/部分不补零；dashboardHome/KPI N、跨页 B |
| 同页 | GET todos/today、dashboard/today-actions、dashboard/pending-settlements、finance-radar | 待办、建议、结算、雷达分开加载/失败/重试，来源/陈旧警告保留；N/B |
| 同页 | 手动 GET goals + progress、monthly-budgets + comparison；新增手动 GET ledger/txns?page=1&pageSize=5 | 复用已有接口，最多3目标/5流水，新增展示无写入；N；页面 B，所有速览组合深测为 N |
| 同页 | GET orders/{id}、nav；POST settlements/preview→人工二次确认→orders/{id}/settle | 首页原人工处理链保留、按钮接回编辑器；四方向/阻断/取消/token N；共享结算组件 B |
| `/ledger` | GET ledger/txns、/{txnId}；GET ledger/stats/summary、trend、breakdown、top | 筛选、分页、搜索、账本关联详情、统计保留；真0/未知分类/旧记录/失败空态 N/B |
| 同页/统一录入 | POST ledger/txns、quick-entry、transfer、custody-transfer、refund、reimburse；PUT ledger/txns/{id}；DELETE ledger/txns/{id} | 人工收支/转账/托管/投资/退款/报销/编辑/撤销原链、DTO/日期/精度保留；核对后提交；N，expense 提交不确定锁 B |
| 同页/MMF信封 | GET accounts、products、holdings；原 PUT accounts/{id} 固定分配额与原流水调用 | 多步骤业务不伪称单个预览事务，不因外观改金额/份额；N/S，真实多步骤结果 NOT_RUN |
| `/drafts` | GET drafts、/{id}、/{id}/history；POST parse-text→draft-from-intent | 候选只生成DRAFT；来源/sourceRef和缺项保留，无自动confirm；N/B |
| 同页 | PUT drafts/{id}；GET holdings/product/{id}/by-account；POST drafts/{id}/preview→新鲜预览/二次确认→confirm | 七方向（收入、支出、转账、买入、申购、卖出、赎回）N；expense 完整链、sell来源/份额/预览 B；财务语义不合并 |
| 同页 | POST drafts/{id}/ignore、reopen、copy-confirmed | 忽略/恢复/复制需人工操作；CONFIRMED/IGNORED不直接再确认；生命周期/重复/旧响应 N，忽略只读和文本创建 B |
| `/orders` | GET orders、/{id}、accounts、products、nav/by-date；POST orders；POST orders/{id}/cancel | 列表、详情、资金线、筛选、新建和取消保留；新建复用 NewOrderModal；N/B；无扩展家庭订单；授权门禁见 AUTH-001 |
| 同页 | 打开 SettlementConfirmModal；POST preview→人工核对→settle | 旧隐式计算改为显式输入/服务器影响预览；深链仅打开表单，不自动预览/确认；N/B |
| `/settlements` | GET settlements/pending、history、history/{orderId}；GET nav/by-date；POST settlements/preview；POST orders/{id}/settle | 四方向、日期/净值/金额/份额/费用、freshPreviewToken/二次确认保留；历史digest只读不回放；N/B |
| `/accounts` | GET accounts、/{id}；POST accounts、PUT accounts/{id}；PUT accounts/{id}/balance | 树/平台/信封、MMF、改名、新建/编辑/启停/余额调整保留；完整父子搜索、负余额、未知行情；N/B，真实余额写入 NOT_RUN |
| 同页 | GET products?channel=OTC、nav/latest、holdings、market/quotes；broker-fee-configs 的 GET/POST/PUT/DELETE | 来源/费率/百分比/上下限及份额原处理链保留；无新家庭scope；N/S，树详情 B |
| `/products` | GET products、/{id}；POST products、PUT products/{id}；GET/POST products/{id}/sell-fee-tiers | 场内/场外、筛选、QDII兼容、费用/偏移/截单/来源/备注保留；费率读取失败阻止覆盖；N/B |
| 同页 | POST products/sort-order、/{id}/refresh-market-data、refresh-all-market-data | 拖拽/键盘排序写原元数据API，失败回退；采集启动不当行情完成，忙碌防重；排序 B、采集等待 N；无产品删除按钮 |
| `/holdings` | GET holdings、market/quotes、nav/latest；详情 GET nav/history、market/bars、market/quotes/history、indicators/history、ledger/txns?productId | 账户来源、清仓/券商/渠道筛选、详情/区间/产品档案保留；缺价格不虚报亏损、已实现收益未提供明确说明；N/B |
| 同页 | POST holdings/import-initial | 仍是人工正式导入，有真实账本副作用；不是演示或预览；清空按钮仅清待导入表单；N/S，生产/真实导入 NOT_RUN |
| `/goal-budget-center` | GET goals、/{id}、progress；POST goals、PATCH goals/{id} | 创建/编辑/暂停/恢复/归档保留；完成率真实0/部分/未知分离；N/B |
| 同页 | GET monthly-budgets、/{id}、comparison；POST/PATCH monthly-budgets；复制下月/六个月回顾/CSV | 原计划元数据、实际质量、缺月、同月多计划、作用域/币种/分页/并发/CSV转义保留；复制不改旧预算；N/B |
| 同页 | POST goals/{id}/forecast、goals/scenarios/compare | 手动只读情景、0%基线、可选后端数学年率、2–8目标共享结余/超限保留；不执行转账/冻结；N/B |
| `/strategy-lab` | GET backtest-lab/datasets、recent、runs、runs/{id}；POST runs、runs/compare、research、evidence | 后端数据集/策略白名单、原日期/hash/参数/指标、失败/超时/历史分页/导出保留；仅研究；N/B |
| 同页 | GET/POST research-plans；GET/PATCH /{id}；POST /{id}/runs | 来源快照/候选、草稿编辑、归档、保存后运行与关联历史保留；冲突/不确定结果不可自动重跑；N/B |
| `/risk-center` | GET/POST risk-watch-rules；PATCH /{id}；GET snapshots/events；POST evaluate；PATCH acknowledge/mute | 规则/手动评估/分页历史/已读/静默只写观察元数据，UNKNOWN不解除；不显示记账/交易动作；N/B |
| `/rebalance-center` | GET/POST allocation-policies；PATCH /{id}；POST evaluate；手动 GET preview | 配置元数据/手动观察/金额守恒情景保留；NOT_MODELED、陈旧/未知收益不隐藏；无执行调仓；N/B |
| `/data-readiness` | GET data-readiness?scope&month；人工复制脱敏摘要 | 四种质量/来源/时间/陈旧/原因保留；SCHEMA不能实时验部署，不执行迁移；N/B |
| `/settings` | GET/PUT users/me；POST users/change-password | 服务端资料/密码校验/失败与成功清空保留；无本地用户库；N/B |
| 同页 | GET/POST families/members；DELETE /{userId}；PUT /{userId}/role | 核实管理员、移除/角色二次确认、最后管理员后端门禁保留；成员/无家庭/失败只读；N/B |
| 同页 | 浅/暗/系统/旧版、奶油/蓝/桃外观 | 只持久化 version/mode/palette，存储拒绝/损坏不影响业务；主题不请求业务、不重置表单；N/B |
| `/portal` | GET users/me；POST auth/logout；保留PC/H5/原站点href | 真实目录/独立外站说明、noopener noreferrer与退出清缓存保留；外站请求在fixture中中止；N/B |
| `/login` | POST auth/login、auth/register | 真实服务端认证；键盘/等待防重/弱网手动重试/错误/站内白名单redirect保留；仅fixture凭据 B/N |
| `/portal-login` | POST auth/login | 同一身份机制，只有Portal回跳、没有独立注册；401/403独立登录边界；N/B |

全局原“刷新数据（重算历史余额）”仍调用 POST ledger/recalculate-all-balance-history，明确标为维护写操作，不是只读刷新；原行情采集也保留显式按钮。本轮均未执行。研究、风险、配置、目标/预算、就绪页不显示全局财务快捷动作。不存在后端支持的批量自动结算、券商下单、自动转账/跟投、财富排名或AI健康分，不新增这些按钮。

## 核心异常及安全回归

| 场景 | 本轮证明与实际边界 |
| --- | --- |
| 真实空数据/未知/部分 | Node KPI、持仓、流水、预算、目标、雷达、就绪及结算列表验证 [] 与null/缺值/非有限数区分；B流水空列表/已知0、缺净值持仓、预算UNKNOWN、配置UNKNOWN、空诊断证据 |
| 重复点击 | Node人工录入/七方向草稿/四方向结算确认锁、预览/订单取消；B登录Enter等待期间只请求一次，完成状态不可再次确认；无自动重试财务提交 |
| 失败/手动恢复 | Node读失败清旧结果、迟到成功/失败不覆盖新请求；B流水、持仓、草稿、结算、研究、风险、就绪和家庭列表人工重试 |
| 401/403/旧会话 | B双登录边界清token/cache、跨标签替换身份、迟到旧账户响应丢弃；B FAMILY规则403及PERSONAL/FAMILY诊断403；N各财务页面token/user/family/卸载失效；不证明后端owner过滤 |
| 过期preview | N字段/账户/资金线/身份变更、关闭、重复、409/400/401/403、旧token/错误orderId/type/status全部拒绝；B编辑备注失效、取消二次确认无写、409需重预览 |
| 财务链语义 | N所有草稿方向、统一录入各API DTO、四方向结算；BUY/SUBSCRIPTION原付款/应收不再次扣款，SELL/REDEMPTION不在确认草稿时结算；B只读预览与人工确认，所有写请求只变内存fixture |
| 数据质量不丢失 | N/B保留schema未知、来源hash/日期、陈旧行情、缺费用/净值/份额、NOT_MODELED、研究失败和计划/实际差异；技术用语收进说明不取消门禁 |

本轮 **financialWrites=0 表示真实网络/真实财务写入为0**；expense/草稿confirm/结算settle等fixture POST确实被人工触发并mock，在结果中另有 fixtureWrites/fixtureConfirmWrites/confirms 字段。不能把“0”解读成未测试提交，也不能把fixture成功解读成真实交易成功。浏览器未逐个完成所有七方向及每一种编辑/导入财务动作，完整状态组合通过Node API mock覆盖；真实业务E2E为NOT_RUN。

## 本次独立新鲜验收与权威证据

原两轮run（额度deferred及续跑failed）、旧stdout/stderr、旧source与工作树完整保留。旧source的Node/类型/浏览器摘要不能证明本次执行。本任务沿原L3、approval、allowed_paths、依赖与禁止动作在全新clean worktree重建，生成新source commit和逐文件内容哈希；不再次调用已经完成的工程模型，不重放item_41或其他历史工具命令。

固定控制器执行全PC Node、所有21局部配置、完整TS与隔离baseline诊断多重集合、shared构建、原完整post-task-compile-hook MaxFixRounds0、diff/clean/hash门禁；全量历史44条必须保留真实exit2和新增诊断对照，不能改成全量类型PASS。后端构建使用既有DskipTests，后端单测/数据库集成NOT_RUN。

独立Playwright CLI+Edge冷会话逐一验收12个fixture：portalAuth、accountsProducts、holdingsExperience、ledgerExperience、draftWorkflow、ordersSettlements、goalsPlanning、strategyLab、riskRebalance、readinessSettings、navigation、foundation。主portal保留14业务页×1440/1100/1024/760/390五屏宽矩阵和严格标准widths字段；另外11个fixture验证人工业务操作、状态/质量、权限拒绝、主题及导航。每个会话open/run-code/close真实记录，serviceWorkers禁用。

本地固定3110/3113/3114/3115只服务构建preview，3107/3108仅导航/基础源HTML的本树锁定Vue转换，均无应用代理或后端。所有业务HTTP被精确mock，未知API/非API fetch/xhr、外部origin中止并失败，真实财务写入0；fixture写入另计。每份回执必须显式PASS、完整隔离boundary、pageerrors/unknown请求空、interceptedRequests大于0，完整脚本回显和最终fixture哈希一致。原历史错误不删，新鲜最终错误不得忽略。

具体真实数量、退出码、原字节日志、哈希和冷会话Result发布到AiCore `reports/work-rescue/20261013-r220-verification/`；本报告与候选内types/inventory数据仅作审查/内容来源，不能代替新run的checks/lineage/verification和正式Delivery。source/integrated tree一致、任务和原run字节未漂移、全部门禁通过才由原Runtime.integrate/Finalizer交付。Goal总数15/15之后仍须真实Hermes企业微信汇总receipt；工程交付不授权生产部署。

截图/自动断言不证明owner主观设计、真实触屏、屏幕阅读器、真实角色/JWT/Nginx或生产数据；对应授权实测仍NOT_RUN。没有为完成本任务连接生产或修改后台授权缺口。

## Mapper/SQL只读配套数量闭环

按commit-branch-consistency-review只读采集器枚举目标树，完整Mapper/语句/脚本清单和28对象逐对象矩阵见 [database-coverage.json](../web/pc-app/delivery/r2-20-database-coverage.json)。未执行SQL或连接数据库；仅证明仓库脚本覆盖，不证明生产schema、脚本适用引擎、完整迁移或恢复演练通过。

| 项目 | 数量 |
| --- | --- |
| XML / 注解Mapper | 20 / 5 |
| Mapper总数 / 已解析 / 未完整解析 | 25 / 25 / 0 |
| SQL语句 / 引用对象 | 164 / 28 |
| SQL脚本 / 涉及对象 | 44 / 66 |
| 有脚本覆盖 / 无脚本覆盖 | 28 / 0 |
| 通用init建表缺口 / 动态表名 | 0 / 0 |
| 本轮新增引用 / 新增无脚本覆盖 | 0 / 0 |

28=28+0，25=25+0。R2全部提交未改backend/shared/SQL/Android/scripts或锁文件，无新migration/服务/构建依赖漏交。新UI调用仍沿既有shared与后端链；既有接口不足按下面G项处理。

## 发布不通过明细与原门禁闭环

| 编号/等级 | 文件位置与证据 | 影响 | 最小修复/复核要求 |
| --- | --- | --- | --- |
| AUTH-001 BLOCKER | OrderController.java:82按status调用全局getOrdersByStatus；OrderService.java:732仅按状态查询；OrderController.java:92详情、:226取消与OrderService.java:641未见统一owner校验；SettlementController.java:61 pending | mock清缓存不能防后端跨用户读取/取消；R2-06 G06已指出 | 另行授权后端owner/家庭可见性修复或提供可信授权证据；以两个无关联用户/家庭、ADMIN/MEMBER、按状态/ID/取消/待结算越权测试复核；本轮禁止后台修改/真实连接 |
| TYPE-001 WARNING | NewOrderModal.vue:218、QuickEntryModal.vue:61等44条，完整位置在types JSON/控制器log | 全量PC类型退出2，不能作为零诊断发布 | 后续独立清理未使用符号；当前只接受44条严格一致，任何新增阻断 |
| ROLE-001 NEEDS_CONFIRMATION | router/index.ts:100附近仅凭token存在，真实JWT/角色及Nginx部署未测试 | mock不证明有效权限/发布深链 | 真实账号与目标环境另行授权验收；禁止本任务为补证连接生产 |
| DESIGN-001 NEEDS_CONFIRMATION | 原型ZIP未核验；本文本地截图和主题断言 | 不能声称owner认可信息密度/视觉及触屏/辅助技术 | owner确认17页主观效果，另测真实设备、读屏、IME、密码管理器 |
| DELIVERY-001 NEEDS_CONFIRMATION | 本地Git/tests存在，外部Runtime.integrate/Delivery Manifest/企业微信receipt未核验 | 不能宣布整个第二轮Goal完成 | AiCore校验每张任务commit/tests/Delivery，Hermes核验goal_only企业微信汇总收据 |

| R2-06 原门禁 | 本轮闭环 |
| --- | --- |
| G01/G02 未实现绩效/单持仓详情API | 不新增调用；持仓详情复用行数据和原图表接口；S/N |
| G03 account ownerType/scope差异 | 保留个人范围，不新增家庭/成员账户汇总；真实隔离NOT_RUN |
| G04/G08 历史类型/Products私有导入 | 当前focused全过；控制器44条全量历史不变；不借旧数字伪造改善 |
| G05 重算写接口permitAll | MainLayout.vue:28明确维护写操作，不能只读刷新/主题触发；SecurityConfig既有权限事实未修复，本轮不点该按钮 |
| G06 订单授权 | AUTH-001上线阻断仍在；不能靠前端过滤证明修复 |
| G07 余额Void/旧DTO不一致 | 原重读/服务器计算保留，没有新增假响应或类型屏蔽；API实际契约须真实环境验收 |
| G09/G10 无执行/排名/健康分/事件总流API | 不迁移假按钮；最近流水只用现有GET；没有自动业务 |
| G11 观察/数学/研究质量边界 | UNKNOWN/NOT_MODELED/hash/日期/警告N/B保留 |
| G12 原型/真实环境证据不足 | DESIGN/ROLE/DELIVERY待确认，不扩大授权或代造PASS |

## 发布检查单与安全回滚方案（仅文档，未执行）

1. owner确认主观设计；真实角色/后端隔离AUTH-001通过后，才可讨论发布批准。工程回归完成不会自动推进生产。
2. AiCore保存本轮source commit、raw命令JSONL、fixture回显/hash、types/路由/SQL覆盖证据；核对R2-06～20各任务Delivery lineage，Hermes最终receipt成功后才可宣布Goal完成。
3. 未来经单独发布授权，以精确source SHA构建静态PC制品，记录制品hash、目标release/base路径和Nginx深链配置，保留当前线上已知制品。不能把本地preview的成功写成生产release。
4. 旧版外观可从设置选择legacy，只影响外观，**不等于恢复旧页面源码**。完整UI回退必须经人工审阅，在另一个获准发布流程重新选定旧静态制品/源commit；本工作树不checkout/reset/restore、不自动部署旧源码。
5. 因本轮不改backend/SQL/shared，计划回滚只涉及PC静态制品与外观偏好。财务记录保持服务器事实；不回滚数据库、不删除流水、不逆向交易、不回放previewDigest/token，不重新提交未确认结果。
6. 回退后人工核验基路径/深链、双登录、14导航、只读来源、401/403和人工预览确认边界。旧源码仍有原权限/维护写入口风险，回退本身不能视为解除AUTH-001。

独立数据库恢复/双引擎演练、生产行为验收和实际回滚为NOT_RUN。本任务不生产部署、不创建后继业务任务；正式commit/push、交付及汇总由原AiCore/Hermes合法链处理。
