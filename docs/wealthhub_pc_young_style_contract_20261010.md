# 财富中枢 PC 年轻个性版：视觉、全路由与操作契约

- 任务：`task-aiw-r2-06-ui-contract-mapping-20261010`；核对日期：2026-10-11（Asia/Hong_Kong）。
- 源码基线：`TimeLordTTY/MyDCA-Board@v2`，`e87d1044a8390b001af2e0db564531d14b61ae96`；执行前 `git fetch origin v2` 后 HEAD 与 origin/v2 相同，初始工作树干净。
- 前置依赖：`task-aiw-r1-05-aicore-unified-backlog-issue2-20261010` 的任务文件已在 AiCore inbox `done` 目录核对；不把该目录状态解释成整个第一轮 Goal 已验收或已通知。
- 本次唯一交付文件为本文；不实现界面、不修改 shared/backend/SQL/Android/runtime，不连接数据库或服务器，不调用业务 HTTP，不执行财务操作，不部署、不推送、不派生任务。
- 事实顺序：[文档索引](DOCUMENT_INDEX.md) → [当前开发状态](CURRENT_DEVELOPMENT_STATE.md) → 当前代码/测试 → 历史专项文档。Git 能力、目标环境部署、人工验收分别记录；本文不代表生产已包含当前 HEAD。

## 1. 视觉依据与设计方向

方向是「个人财富生活空间」：纸感背景、大数字、日常目标与待处理事件；让用户能先看清资产和来源，再主动处理业务。中文操作标签保持具体，例如“查看预览”“确认创建内部订单”“确认结算”，不用“立即赚钱”“一键执行”等承诺。

`WealthHub-年轻个性版-20261010.zip` 未在本次执行器读取，未核对其中截图、代码、入口原名及顺序。以下颜色、半径、导航尺寸来自批准任务中已从 `personal.css` 提取的参数；不能声称本文是对 ZIP 的完整源码审计。除这些参数外，尺寸、排版、暗色文字与交互细节均为本次设计建议。

| 变量建议 | 值 | 用途与边界 |
| --- | --- | --- |
| `--wh-page` | `#f7f6f2` | 奶油白页面背景 |
| `--wh-surface` | `#fffefa` | 纸白卡片、表单与弹窗 |
| `--wh-ink` | `#232521` | 主要文字、真实金额 |
| `--wh-muted` | `#85867d` | 装饰和次要元信息；小字号重要说明须换更深文字并验证对比度 |
| `--wh-line` | `#e7e6de` | 分隔线；表单边界与焦点不能只靠此浅线 |
| `--wh-accent` | `#3458dc` | 主动作、选中导航与焦点 |
| `--wh-positive` | `#368363` | 有证据的成功状态；涨跌方向按现有语义，不跟配色模式一起反转 |
| `--wh-mint-bright` / `--wh-mint-soft` | `#c5ee91` / `#eaf3dc` | 首页重点卡与轻背景，不直接表示 READY 或收益 |
| 暗色页面 / 卡片 | `#20221f` / `#292c27` | 原参数；暗色主文字建议 `#fffefa`，辅助文字须实测 |
| 蓝色 / 桃色外观 | `#9bc1ff` / `#ffc2a4` | 外观装饰面；危险、阻断、UNKNOWN 语义独立 |
| `--wh-card-radius` / `--wh-pill-radius` | `23px` / `999px` | 卡片与胶囊动作；紧凑表格仍保留清晰行边界 |
| `--wh-rail-width` / `--wh-nav-width` | `82px` / `222px` | 图标轨与展开导航；完整导航默认可见 |
| 间距建议 | `8 / 12 / 16 / 24 / 32px` | 卡片内24px、卡片间24px，表单可紧凑至16px |
| 字体建议 | 中文本机衬线标题＋本机无衬线正文 | 标题可用 Songti SC/SimSun；正文 PingFang SC/Microsoft YaHei；无外网字体请求 |
| 数字 | `font-variant-numeric: tabular-nums` | 金额右对齐，币种/单位/精度可见；展示四舍五入不改变提交数值 |
| 过渡建议 | `160ms`，仅颜色/阴影/有限位移 | `prefers-reduced-motion` 时取消位移；数据加载不动画成虚拟增长 |

组件外观层预期复用：页面壳、导航轨、页面标题/作用域说明、AssetSummary、PaperCard、StatusBadge、EmptyState、ErrorState、DataSourceNote、FilterBar、DataTable、FormSection、ActionButton、PreviewPanel、ConfirmDialog。它们是设计职责，当前没有这些同名新组件，也不增加新 API。业务加载、DTO、提交与权限继续由既有页面/核心组件管理。

### 1.1 导航、触屏与键盘

- 大屏默认固定展开222px，14个业务入口完整存在；82px紧凑轨是用户可选外观。鼠标 hover 与键盘 `focus-within` 都可展开，展开覆盖侧边区域，不推挤正在填写的表单。不能把唯一入口藏在 hover 中。
- 紧凑轨每个入口保留 accessible name、当前页 `aria-current`、文本提示，以及始终可见的“展开完整导航”按钮。触屏点击按钮固定展开，按钮带 `aria-expanded`/`aria-controls`；不能首次点入口只展开、第二次才导航。
- 窄屏使用显式目录抽屉，开关可见；Esc 关闭、焦点留在抽屉内并返回触发按钮，当前入口可见。导航自身可滚动，末项设置不会被遮挡；正文沿用 MainLayout 的独立滚动边界。
- Tab 顺序为跳到主内容 → 导航 → 页面工具 → 内容；按钮最小触控区建议44px。抽屉与业务确认框的 Esc 只取消，不提交。表格可以横向滚动，页标题和状态不随表格消失。
- Loading 使用骨架/“正在读取”，空态、无权限、失败和未知分别呈现；错误 `role=alert`，加载/结果 `aria-live=polite`。图表提供文字表格替代；状态不能只依赖颜色。

### 1.2 外观偏好与业务隔离

明暗模式（浅色/深色/跟随系统）与奶油/蓝/桃配色独立。若持久化，仅存有版本和枚举白名单的外观偏好，例如 `wealthhub.appearance.v1`；不存金额、账户、订单、草稿、研究结果、登录凭据。存储拒绝或格式损坏时回到默认浅色奶油，页面照常工作。

这条新偏好规则不改动既有真实认证 token 的存储机制（见第3节）。切换主题只改 CSS 外观：不得触发 `data-refresh`、业务请求、重算、解析、preview、confirm，不能清除选择、编辑输入或新鲜预览，不能改变个人/家庭作用域、业务日期、精度或任何状态。外观不能成为“演示模式”；正式页面不提供模拟资产开关。

## 2. 路由与导航信息架构（全量）

源：[router/index.ts](../web/pc-app/src/router/index.ts) 第9–116行、[MainLayout.vue](../web/pc-app/src/layouts/MainLayout.vue) 第92–196行。路由有17个具名页面（14个业务子页面＋3个独立页面）和1个 `/` 容器重定向；现有 MainLayout 只有13项导航，`/settlements` 已有独立页面但未列入 navItems。新导航必须补足它的可达性，不通过删除旧路由解决。

| 编号 | 真实路由 / name | 建议导航分组/标签 | 当前认证与保留事项 |
| --- | --- | --- | --- |
| R00 | `/`（无 name） | 不作单独侧栏项 | 受保护；重定向 `/dashboard` |
| R01 | `/dashboard` / Dashboard | 生活总览 / 总览 | 继承 requiresAuth |
| R02 | `/ledger` / Ledger | 日常收支 / 流水 | 保留筛选、分页与关联详情 |
| R03 | `/drafts` / DraftInbox | 日常收支 / 草稿箱 | 独立候选区，不能混作正式流水 |
| R04 | `/orders` / Orders | 投资记录 / 订单 | 原标签“订单&结算”；新名称不删结算操作 |
| R05 | `/settlements` / Settlements | 投资记录 / 待结算与审计 | 独立链接；保留订单页和首页进入结算的路径 |
| R06 | `/accounts` / Accounts | 我的资产 / 账户 | 保留平台/信封/叶子账户层次 |
| R07 | `/products` / Products | 我的资产 / 产品 | 保留场内/场外管理 |
| R08 | `/holdings` / Holdings | 我的资产 / 持仓管理 | 保留账户来源、已清仓、导入和详情 |
| R09 | `/goal-budget-center` / GoalBudgetCenter | 生活计划 / 目标与预算 | 保留目标、预算、回顾、情景子功能 |
| R10 | `/strategy-lab` / StrategyLab | 观察研究 / 策略实验室 | 真实历史回测，不作资产/交易控制台 |
| R11 | `/risk-center` / RiskCenter | 观察研究 / 风险观察 | 明确只写观察元数据 |
| R12 | `/rebalance-center` / RebalanceCenter | 观察研究 / 配置观察 | 不将路由名称理解为可执行调仓 |
| R13 | `/data-readiness` / DataReadiness | 来源与管理 / 数据就绪检查 | 只读诊断，SCHEMA 能力边界醒目 |
| R14 | `/settings` / Settings | 来源与管理 / 设置 | 资料/密码/成员管理与外观分区 |
| R15 | `/portal` / Portal | 独立站点目录入口 | requiresAuth；不计业务侧栏项 |
| R16 | `/login` / Login | 独立登录/注册 | requiresAuth=false；已有 token 跳总览 |
| R17 | `/portal-login` / PortalLogin | 独立目录登录 | requiresAuth=false；已有 token 跳 Portal |

URL 均为 router 内部路径；部署保留 `createWebHistory(import.meta.env.BASE_URL)`，不得丢失 `/wealth-hub/` 基路径。Portal 原有 PC/H5/游戏墙/诗词站/情晓录链接仅为站点导航，外站使用现有 `noopener noreferrer`，不移为财富业务路由。书签、返回键、redirect 参数和 Products → Holdings 的 `productId` 查询保留；不存在的深链不伪装为新业务页面。

### 2.1 原型17入口的处理登记

批准任务只提供“17侧栏入口”数量和违规功能类别，没有17项原名/URL/顺序。下表以 P01–P17 为审计槽位，**不是猜测出的原型菜单**；本次全部标“不迁移（原入口未核实）”。正式导航已由上表独立闭环，不需要等待 ZIP 才能确定17个真实具名页面。以后若获得 ZIP，只可补充视觉对应证据；任何业务按钮仍须按本文真实链验收。

| 原型槽位 | 原名/路径证据 | 本次迁移决定 |
| --- | --- | --- |
| P01 | 未提供、未读取 | 不迁移原入口；只可参考已给视觉变量 |
| P02 | 未提供、未读取 | 不迁移原入口；只可参考已给视觉变量 |
| P03 | 未提供、未读取 | 不迁移原入口；只可参考已给视觉变量 |
| P04 | 未提供、未读取 | 不迁移原入口；只可参考已给视觉变量 |
| P05 | 未提供、未读取 | 不迁移原入口；只可参考已给视觉变量 |
| P06 | 未提供、未读取 | 不迁移原入口；只可参考已给视觉变量 |
| P07 | 未提供、未读取 | 不迁移原入口；只可参考已给视觉变量 |
| P08 | 未提供、未读取 | 不迁移原入口；只可参考已给视觉变量 |
| P09 | 未提供、未读取 | 不迁移原入口；只可参考已给视觉变量 |
| P10 | 未提供、未读取 | 不迁移原入口；只可参考已给视觉变量 |
| P11 | 未提供、未读取 | 不迁移原入口；只可参考已给视觉变量 |
| P12 | 未提供、未读取 | 不迁移原入口；只可参考已给视觉变量 |
| P13 | 未提供、未读取 | 不迁移原入口；只可参考已给视觉变量 |
| P14 | 未提供、未读取 | 不迁移原入口；只可参考已给视觉变量 |
| P15 | 未提供、未读取 | 不迁移原入口；只可参考已给视觉变量 |
| P16 | 未提供、未读取 | 不迁移原入口；只可参考已给视觉变量 |
| P17 | 未提供、未读取 | 不迁移原入口；只可参考已给视觉变量 |

| 任务已确认的原型功能类别 | 标记 | 正式替代/限制 |
| --- | --- | --- |
| 虚构交易、浏览器交易计算 | 不迁移 | 仅内部订单/人工记账/结算真实 API；无券商下单接口 |
| 虚构研究结果、随机收益曲线 | 不迁移 | 策略实验室真实数据集、历史运行与证据；失败不能生成成功图 |
| localStorage 本地账本/模拟资产 | 不迁移 | 正式账本 GET/人工提交；空资产为真实0或未知，不灌样例 |
| 浏览器本地登录/测试凭据/测试姓名 | 不迁移 | `/auth/login`、`/auth/register`、`/users/me`；无独立本地用户库 |
| 原型演示按钮、未核实入口 | 不迁移 | 没有已验证接口则删除，或明确文字“当前未支持”且不可点击 |

## 3. 认证、DTO、权限与副作用约定

所有下文 API 路径默认加 `/api/v2`；共享入口是 [api/index.ts](../web/shared/src/api/index.ts)、[types/index.ts](../web/shared/src/types/index.ts)、[stores/index.ts](../web/shared/src/stores/index.ts)。不得另写浏览器业务模型替代共享 DTO。

| 规则 | 当前证据 | 新界面保留/门禁 |
| --- | --- | --- |
| token / JWT | [client.ts](../web/shared/src/api/client.ts) 第12–83行；[user store](../web/shared/src/stores/user.ts)、[auth.ts](../web/shared/src/api/auth.ts) | 实际 token 在 localStorage，拦截器发送 Bearer；路由只检查 token 是否存在，不证明有效身份或家庭权限 |
| 401/403 | client.ts 第47–69行 | 当前二者均清 token 并跳登录；不能声称403只在原页提示。页面仍要清旧证据并可人工重试；本任务不修改 shared |
| 账户/持仓作用域 | [AccountController](../backend/src/main/java/com/timelordtty/dca/controller/AccountController.java) 第44–50行；[HoldingController](../backend/src/main/java/com/timelordtty/dca/controller/HoldingController.java) 第109–136行 | `PERSONAL`、`FAMILY_ALL`、`MEMBER + memberUserId`；家庭/成员要求管理员。shared 的账户 `ownerType` 参数不等价于后端 `scope`；当前页面默认个人，不能擅自新增全家切换 |
| 总览作用域 | [DashboardController](../backend/src/main/java/com/timelordtty/dca/controller/DashboardController.java) 第56–73行 | `viewType=personal/family`；家庭校验管理员，与账户 scope 不同 |
| 目标/预算/风险/配置/诊断 | [GoalTrackingService](../backend/src/main/java/com/timelordtty/dca/service/GoalTrackingService.java) 第28–35行；对应 MonthlyBudgetService/RiskWatchService/AllocationPolicyService/DataReadinessService | `PERSONAL/FAMILY`；家庭要求家庭存在及 assertAdmin。不得因能看家庭账户推导有目标或规则权限 |
| 研究/历史 | [ResearchPlanService](../backend/src/main/java/com/timelordtty/dca/service/ResearchPlanService.java) 第86–119行 | 依 ownerUserId/ownerFamilyId 可见性查找；不概括为所有 FAMILY 操作都要求 ADMIN |
| 家庭管理 | [FamilyController](../backend/src/main/java/com/timelordtty/dca/controller/FamilyController.java) 第75–153行；[Settings](../web/pc-app/src/views/Settings.vue) | 成员接口返回角色；UserInfo 无 role，不能凭 familyId 判管理员。后端 add/remove/updateRole 调用 assertAdmin；保留最后管理员等服务端限制 |
| 账户用途与树 | [account store](../web/shared/src/stores/account.ts) 第18–62行；[types/account.ts](../web/shared/src/types/account.ts) | REAL/VIRTUAL、叶子/父账户、SPENDABLE/RESERVED/INVESTABLE、余额与 reservedAmount 独立；信贷不计可用资金。一般 cashLeafAccounts 的筛选不替代草稿后端 active/币种/用途校验 |
| 产品 | [product store](../web/shared/src/stores/product.ts) 第20–49行；[types/product.ts](../web/shared/src/types/product.ts) | 保留 EXCHANGE/OTC、isQdii/isqdii 兼容、启用状态、费用及日期。store 失败会清产品列表，空列表不证明没有产品 |
| 状态/数据质量 | dashboardKpiTruthModel、goalBudgetModel、financeRadarModel、dataReadinessModel | 不把各模块 enum 合并。真实0保留；null/缺项/非有限数=未知；PARTIAL只列已知部分；UNKNOWN不表示0/达成/无风险 |
| 过期响应 | goalProgressGlanceModel、monthlyBudgetGlanceModel、budgetReviewModel、todayTodoModel、pendingSettlementListModel | 保留请求代次、身份变更、卸载/取消清理；不能跨账号显示上一账号数据，切作用域不复用旧可编辑证据 |

副作用分类：**读**=不写财务数据；**候选**=写草稿/生命周期；**元数据**=写计划、观察、研究；**财务**=可能改账本、账户、持仓、订单占用。POST 不必然是财务操作，GET 也不能仅凭方法名推定业务完整。特别是草稿 preview 会存预览 JSON 并记生命周期审计，虽不写正式财务，不能描述为完全无持久化。

旧接口权限缺口须如实保留：[OrderController](../backend/src/main/java/com/timelordtty/dca/controller/OrderController.java) 的无状态订单列表按当前 userId，带 status 则调用 `getOrdersByStatus`；[OrderService](../backend/src/main/java/com/timelordtty/dca/service/OrderService.java) 第725–747行按状态/ID读取未见 owner 过滤；SettlementController 的 pending 也调用该全局状态查询。不能声称“所有订单读入口都有家庭隔离”。结算计算另有 owner/可见资金账户校验；这不能替代列表/详情/取消权限验证。新外观不得扩展这些入口权限，相关跨角色验收未通过前不得上线；本任务不改后端来填补。

## 4. 逐页 API 与操作契约

### R01 Dashboard：资产与生活概览

证据：[Dashboard.vue](../web/pc-app/src/views/Dashboard.vue) 第524–596、693、903–925行；[dashboard API](../web/shared/src/api/dashboard.ts)、[KPI 模型](../web/pc-app/src/components/dashboardKpiTruthModel.ts)。

| 功能/动作 | 方法与真实路径/DTO | 必留状态与人工边界 |
| --- | --- | --- |
| 四项资产摘要 | GET `/accounts`、`/dashboard/asset-overview`（AssetOverview）、`/holdings`；GET `/market/quotes`（场内）、`/nav/latest?productId`（场外） | 建议首屏依次净资产、可用资金、持仓市值、负债；均沿用 kpiTruth。净资产=现金＋持仓−负债且来源齐全；可用资金=sum(balance−reservedAmount)，不是全部可消费资金 |
| 其他真实指标/持仓构成 | 同一快照派生 reserved、spendable、totalPnl、exchangePnl、otcPnl、todayPnl、monthInflow | 原指标不能丢；部分持仓只显示已读估值；不能用缺行情造成虚假亏损 |
| 今日待办/近期事件 | GET `/todos/today`（TodayTodo）、`/dashboard/today-actions`（TodayAction[]）、`/dashboard/pending-settlements`（Order[]） | 独立加载；todayTodo 的覆盖数、截断详情、时间与未知保留；跳转按白名单，不直接执行 actionPath/actionUrl |
| 预算/目标 | GET `/monthly-budgets`＋`/{id}/comparison`；GET `/goals`＋`/{id}/progress` | MonthlyBudgetGlance/GoalProgressGlance 保留手动加载、作用域/币种、分页上限、取消、空态。目标最多3项，不把展示数量当全量；倒计时相对数据日期 |
| 最近流水（设计补充） | GET `/ledger/txns?page=1&pageSize=5`（LedgerListResponse），需要时 GET `/{txnId}` | 现首页未接最近流水块；可以复用已有 GET，不能假称已实现。仅正式返回、按服务端排序展示，失败有重试 |
| 每日财富雷达 | GET `/finance-radar?scope=PERSONAL`（FinanceRadar） | 保留 warnings、来源日期、UNKNOWN、旧快照标记；无客户端 Outbox 推定数量 |
| 从待结算进入详情 | GET `/orders/{id}`、净值 GET；POST `/settlements/preview` → 二次确认 → POST `/orders/{id}/settle` | 保留第5节完整链。首页是人工入口，不是自动结算面板 |

首屏顺序：标题/数据时间 → 四项资产摘要 → 近期事件与真实待办 → 本月预算/目标 → 最近流水 → 雷达与持仓详情。摘要可呼出来源说明；不显示原型资产样例，不计算未经支持的财富排名/健康分。

### R02 Ledger：流水、统计与手工记账

证据：[Ledger.vue](../web/pc-app/src/views/Ledger.vue) 第526–605、874–940行；[ledger.ts](../web/shared/src/api/ledger.ts)、[ledgerStats.ts](../web/shared/src/api/ledgerStats.ts)、[types/ledger.ts](../web/shared/src/types/ledger.ts)。

| 功能/动作 | 方法/路径 | 保留契约 |
| --- | --- | --- |
| 筛选/分页/详情 | GET `/ledger/txns`（txnType/startDate/endDate/productId/parentAccountId/accountId/note/page/pageSize）；GET `/ledger/txns/{txnId}` | 第1页开始，list/total/totalPages 不用当前页条数代替；显示 postings、关联单、退款/报销 remaining |
| 收支统计 | GET `/ledger/stats/summary`、`trend`、`breakdown`、`top` | 统计复用 LedgerStatsQuery，多值参数由 shared 逗号序列化；scope、币种、过滤与列表口径明确，不用可见页求全月总额 |
| 统一录入/修改 | POST `/ledger/txns`、PUT `/ledger/txns/{txnId}` | UnifiedEntryModal 的双分录、分类、日期、支付组合、关联单保留；是人工正式提交，不宣称草稿或 preview |
| 快速支出/收入 | POST `/ledger/quick-entry` | QuickEntryModal 校验账户、金额、支付线；提交即正式记账，取消无请求 |
| 退款/报销 | POST `/{txnId}/refund`、`/{txnId}/reimburse`（均 `/ledger/txns` 前缀） | RefundModal/ReimburseModal 展示原单、可退/可报金额、账户与日期；服务端校验，不能默认全额自动执行 |
| 删除/冲正/托管划转/MMF | DELETE `/ledger/txns/{txnId}`；POST `/{txnId}/reverse`、`/ledger/txns/custody-transfer`、`/ledger/quick-buy-mmf` | 当前 Ledger 提供人工删除确认；reverse 是 shared 能力，不等于当前 Ledger 有独立冲正按钮。托管划转/MMF 在 UnifiedEntry；MMF快速买入可直接影响账务与持仓，不能统一改叫待结算 |

不增加原型本地账本、自动分类后立即入账、自动退款。删除/取消/资金划转的结果必须等待服务端；重试写请求前先读取结果，不能自动重放财务提交。

### R03 DraftInbox：候选与安全确认

证据：[DraftInbox.vue](../web/pc-app/src/views/DraftInbox.vue) 第999–1369行；[draft API](../web/shared/src/api/draft.ts)、[aiAccounting API](../web/shared/src/api/aiAccounting.ts)、[draft DTO](../web/shared/src/types/draft.ts)。

| 操作 | 方法/路径 | 状态/人工链 |
| --- | --- | --- |
| 状态过滤、详情、历史 | GET `/drafts`（status/page/pageSize）；GET `/drafts/{id}`、`/{id}/history` | DRAFT/CONFIRMED/IGNORED 分开；history脱敏摘要不可换成完整原文 |
| 文本解析/生成候选 | POST `/ai/accounting/parse-text` → POST `/ai/accounting/draft-from-intent`；shared 另有 POST `/drafts` | 解析结果不是正式流水，产品/账户提示不自动匹配真实ID；只保存DRAFT |
| 编辑/影响预览 | PUT `/drafts/{id}` → POST `/{id}/preview` | 只允许DRAFT编辑；改字段清旧预览。展示 missingFields、warnings、confirmSupported、金额/份额/账户/产品及 willCreate* |
| 确认 | 用户二次确认 → POST `/drafts/{id}/confirm` | 当前请求不传 freshPreviewToken；后端锁草稿并重新生成预览校验。不能硬套结算令牌，也不能省略前端最新预览 |
| 忽略/恢复/复制 | POST `/{id}/ignore`、`/{id}/reopen`、`/{id}/copy` | 忽略需人工输入/取消；恢复IGNORED仍是DRAFT；复制已确认草稿生成新来源标识并重新预览/确认；原单结果不复用 |

确认后的副作用依类型区分：EXPENSE/INCOME 正式记账；TRANSFER 双账户现金变动；BUY/SUBSCRIPTION 创建内部PENDING订单并生成付款 CASH CREDIT＋RECEIVABLE DEBIT；SELL/REDEMPTION 仅登记PENDING及来源份额占用，不改现金/持仓、不结算。确认成功应按 confirmTxnId/confirmOrderId 展示真实结果，不一律写“交易已成交”。

### R04 Orders 与 R05 Settlements：订单、结算与审计

证据：[Orders.vue](../web/pc-app/src/views/Orders.vue) 第344–358、558–580、1122–1130行；[Settlements.vue](../web/pc-app/src/views/Settlements.vue) 第111–128行；[order API](../web/shared/src/api/order.ts)、[settlement API](../web/shared/src/api/settlement.ts)、[order DTO](../web/shared/src/types/order.ts)。

| 页面/操作 | 方法/路径 | 保留契约 |
| --- | --- | --- |
| Orders 列表/明细 | GET `/orders`、`/orders/{orderId}`；GET `/nav/by-date`、`/nav/latest` | PENDING/CONFIRMED/CANCELLED/FAILED；产品、资金线、SOURCE/TARGET、日期、费用、关联结算保留。status查询存在第3节权限缺口 |
| Orders 人工登记 | POST `/orders`（CreateOrderRequest） | NewOrderModal/UnifiedEntryModal；四种类型，产品、金额/份额、多来源、目标账户、预期日期保留；这是内部记录，无券商执行 |
| 手续费计算 | POST `/orders/calculate-fee`（productId/accountId/orderType/amount） | 估算不是实际费用；提交与preview仍由服务端重新校验 |
| 人工取消 | POST `/orders/{id}/cancel` | 仅PENDING；保留二次确认。OrderService会恢复相关分录余额、删除关联流水/资金线、释放历史占用并置CANCELLED，绝不是无副作用的隐藏记录 |
| Settlements 待处理 | GET `/settlements/pending`（Order[]） | 当前没有该接口分页；空态与请求失败分开，列表owner限制待核验，不扩展为全家列表 |
| 三处人工结算入口 | POST `/settlements/preview`；实际 PC 经 orderApi POST `/orders/{id}/settle` | Dashboard、Orders、SettlementConfirmModal 都保留同组输入与最新令牌；shared 另有 POST `/settlements/confirm`，同一后端服务，不能双发两条确认 |
| 结算历史/对账详情 | GET `/settlements/history`、`/settlements/history/{orderId}`（SettlementAudit） | 保留操作者/时间、previewDigest、ledgerTxnId、分录/费用/资金线、OK/WARNING/BROKEN 与 reasons。digest只显示，绝不作 freshPreviewToken |

不添加“全部结算”“自动确认”“真实撤单”按钮。BUY/SUBSCRIPTION 的下单付款不在结算再扣一次；SELL/REDEMPTION 的现金/持仓影响只在后续人工结算发生。

### R06 Accounts、R07 Products、R08 Holdings：资产来源管理

证据：[Accounts.vue](../web/pc-app/src/views/Accounts.vue) 第1298–1403、1468–1569行；[Products.vue](../web/pc-app/src/views/Products.vue) 第558–985行；[Holdings.vue](../web/pc-app/src/views/Holdings.vue) 第309–385行。

| 页面/功能 | 方法/路径及 DTO | 状态/人工限制 |
| --- | --- | --- |
| Accounts 树/平台/信封/编辑 | GET `/accounts`、`/accounts/{id}`；POST `/accounts`、PUT `/accounts/{id}`（Account） | 树展开、平台改名、父子关系、REAL/VIRTUAL、启用、归属、币种、用途保留；创建初始余额/份额是业务输入，不能叫外观设置 |
| Accounts 调整余额 | PUT `/accounts/{id}/balance` | 人工余额调整，服务端受控账本影响。shared 声明返回 Account，而 Controller 返回Void，不能靠响应展示新余额，现store会重读列表 |
| MMF份额/关联产品 | GET `/products?channel=OTC`、`/nav/latest`；shared有 GET `/accounts/{id}/mmf-shares` | 保留 initialShares、固定金额、未分配份额及关联产品更改警告；当前页面自行组合净值/树展示，不声称已使用 mmf-shares API |
| 券商费率配置 | GET/POST `/accounts/{id}/broker-fee-configs`；GET/PUT/DELETE `/{feeId}` | 元数据保存、手续费上下限、启用与百分数转比例；删除人工确认；不使用本地费率替换服务端实际费 |
| Products 列表/新建/编辑 | GET `/products`、`/products/{id}`；POST `/products`、PUT `/products/{id}`（ProductMaster） | 场内/场外、关键词/类别/启用过滤、费率、QDII、确认偏移、cutoffTime/dataSource保留；无删除产品接口，不能加删除按钮 |
| Products 顺序/赎回费阶梯 | POST `/products/sort-order`；GET/POST `/products/{id}/sell-fee-tiers` | 排序写后端元数据；拖拽需键盘/触屏移动按钮等价提交；费阶梯保留天数上下界、无限上界与比例精度 |
| 行情采集 | POST `/products/{id}/refresh-market-data`、`/products/refresh-all-market-data` | 后台采集任务启动≠最新行情已获得；重复触发保护/错误/来源时间保留，不在主题切换执行 |
| Holdings 列表/筛选 | GET `/holdings`（HoldingInfo[]）、`/market/quotes`、`/nav/latest` | 场内/场外、市值/成本/盈亏、券商筛选、已清仓保留；行情缺失不默认价格0 |
| Holdings 详情图表/流水 | GET `/nav/history`、`/market/bars`、`/market/quotes/history`、`/indicators/history`、`/ledger/txns?productId` | HoldingDetailModal直接使用选中行＋真实图表请求；日线/净值/行情/指标区间和来源独立，无模拟曲线 |
| 初始持仓导入 | POST `/holdings/import-initial`（InitialHoldingImport[]） | 手工输入/批量校验；无效行跳过前确认，成功清表。后端查建产品/POSITION/INCOME账户并创建ADJUST流水（POSITION DEBIT＋INCOME CREDIT），写份额与成本；不是预览，也不是本地文件演示 |

InitialHoldingImportModal 的“清空所有持仓数据”只清本次表单行，不调用后端删除；新标签应为“清空待导入表单”，避免误导。shared 的 GET `/holdings/{productId}` 在 HoldingController 未发现对应实现，不用于新详情按钮，详情仍复用现有组件数据链。

### R09 GoalBudgetCenter：目标、月度预算、回顾与情景

证据：[GoalBudgetCenter.vue](../web/pc-app/src/views/GoalBudgetCenter.vue) 第51–134行；[goalBudget API](../web/shared/src/api/goalBudget.ts)、[goalForecast API](../web/shared/src/api/goalForecast.ts)、[goalBudget DTO](../web/shared/src/types/goalBudget.ts)、[goalForecast DTO](../web/shared/src/types/goalForecast.ts)。

| 功能/操作 | 方法/路径 | 真实限制与必留状态 |
| --- | --- | --- |
| 目标分页/详情/进度 | GET `/goals`（page从0,size=20）、`/goals/{id}`、`/{id}/progress` | Goal/GoalProgress；目标金额、日期、币种、scope、measure；OK/PARTIAL/UNKNOWN、knownValue、completionRate、completed、asOfDate、overdue分开 |
| 创建/编辑/暂停/恢复/归档 | POST `/goals`、PATCH `/goals/{id}`（GoalConfig） | ACTIVE/PAUSED/ARCHIVED；状态写同一config，不存在自动划款。服务端名称1–200、目标正数/2小数、备注≤2000、合法日期/币种；当前资产汇总非CNY返回未知，不虚构外汇换算 |
| 预算分页/详情/比较 | GET `/monthly-budgets`、`/{id}`、`/{id}/comparison` | Budget/BudgetComparison；计划收入/固定/弹性支出/RESERVE与实际收入/支出/结余分开，unmatchedPostings/warnings保留 |
| 预算新建/编辑 | POST `/monthly-budgets`、PATCH `/monthly-budgets/{id}`（BudgetConfig） | 只改预算元数据，RESERVE是计划预留，不冻结账户；同月多预算分开，无预算不表示实际收支0 |
| 六个月回顾/CSV | 同列表 GET＋手动 comparison GET；本地导出已读取结果 | BudgetSixMonthReview/budgetReviewCsv；连续6月、scope/currency隔离、最多5页/30份对比/并发3/30秒、取消/超限提示；CSV不额外请求、不存业务数据，保留公式注入转义 |
| 单目标现金流情景 | POST `/goals/{id}/forecast`（ForecastRequest/Forecast） | GoalForecastWorkbench 手动发起；PLANNED/ACTUAL_PLUS_REMAINING、期间、budgetId、覆盖声明、额外储蓄保留；当前PC零收益，服务端可选数学年率不成为收益承诺 |
| 多目标共享情景比较 | POST `/goals/scenarios/compare`（ScenarioInput[]/ScenarioComparison） | 2–8个同scope同币种目标；共享结余算一次，overLimit/未知/期限结论保留；请求读事务，不自动分配资金、转账或改变目标进度 |

情景不增加自动达成、自动预算同步、真实收益预测。期间输入按现有PC年份边界拒绝整体超界，不悄悄截短；实际缺失不能生成“未超支”结论。

### R10 StrategyLab：真实回测与研究方案

证据：[StrategyLab.vue](../web/pc-app/src/views/StrategyLab.vue) 第149–239行；[ResearchWorkbench.vue](../web/pc-app/src/components/ResearchWorkbench.vue) 第83–127行；[backtest API](../web/shared/src/api/backtest.ts)、[researchPlan API](../web/shared/src/api/researchPlan.ts)、[types/backtest.ts](../web/shared/src/types/backtest.ts)、[types/researchPlan.ts](../web/shared/src/types/researchPlan.ts)。

| 操作 | 方法/路径 | 必留后端限制/状态 |
| --- | --- | --- |
| 数据集/最近运行/历史/详情 | GET `/backtest-lab/datasets`、`recent`、`runs?page&size`、`runs/{historyRunId}` | 前两者及运行POST目前直接apiClient，其他由backtestApi；真实数据集白名单/路径检查、来源hash、策略版本、SUCCESS/FAILED保留 |
| 手动运行 | POST `/backtest-lab/runs`（data/strategy/version/params） | 后端策略及参数白名单、离线Python引擎/超时；不调用券商，运行可能写历史文件；忙碌防重、失败/结果不确定不能伪装成功 |
| 对比/研究候选/证据包 | POST `/backtest-lab/runs/compare`、`research`、`evidence`（runIds/thresholds） | 对比2–5个历史运行；研究阈值、候选三态、样本/成本等来源限制保留；evidence下载Blob ZIP，错误不下载空成功包；JSON/Markdown仅手动导出已生成报告 |
| 创建/编辑/归档研究方案 | GET/POST `/research-plans`、GET/PATCH `/research-plans/{id}` | 真实candidateId/runIds/thresholds重新校验；创建证据快照与独立paramsDraft，DRAFT/ACTIVE/ARCHIVED，归档不可编辑；失效来源warnings可见 |
| 方案回测及关联历史 | POST `/research-plans/{id}/runs`（仅dataset，120秒）；历史GET并按researchPlanId关联 | 成功与失败记录都保留；编辑冲突须刷新；超时不重复提交，先核对历史。ACTIVE只是研究生命周期，不是启用自动交易 |

所有研究曲线与指标来自真实接口；历史不保证未来。财富资产摘要不引用回测期末资产，研究候选不变成订单。“一键跟投”“策略自动执行”不支持。

### R11 RiskCenter 与 R12 RebalanceCenter：观察元数据

证据：[RiskCenter.vue](../web/pc-app/src/views/RiskCenter.vue) 第52–66行；[RebalanceCenter.vue](../web/pc-app/src/views/RebalanceCenter.vue) 第49–66行；[riskWatch API](../web/shared/src/api/riskWatch.ts)、[allocationPolicy API](../web/shared/src/api/allocationPolicy.ts)。

| 页面/操作 | 方法/路径 | 必留契约 |
| --- | --- | --- |
| 风险规则分页/创建/编辑 | GET/POST `/risk-watch-rules`；PATCH `/{id}` | page从0,size=50；RETURN/DRAWDOWN/ALLOCATION_DEVIATION/STALE/CONCENTRATION/NOTE、阈值/方向/severity/scope；只有规则元数据 |
| 风险证据/历史/手工评估 | GET `/{id}/snapshots`（最新1条）、`/{id}/events`（分页50）；POST `/{id}/evaluate` | 手动评估会写观察快照/提醒元数据；进入页面只读取，不自动评估。保留sourceDataTimestamp/hash、陈旧、UNKNOWN与原因 |
| 已读/静默 | PATCH `/{id}/events/{fingerprint}/acknowledge`、`/{id}/mute`（mutedUntil或null）；config.muted切换另经规则PATCH | OPEN/ACKNOWLEDGED/MUTED/RESOLVED与fingerprint去重；已读不表示风险消失，静默不改证据；未知不自动解除 |
| 配置规则分页/新建/编辑/启停 | GET/POST `/allocation-policies`；PATCH `/{id}` | 目标比例/上下界/收益阈值/分段止盈、enabled、note；0–1比例与百分点不同，不用界面百分数直接发比例 |
| 配置手动观察/情景 | POST `/{id}/evaluate`；主动点击 GET `/{id}/preview` | IN_RANGE/BELOW_BAND/ABOVE_BAND/TAKE_PROFIT_WATCH/UNKNOWN；targetScenario/bandScenario金额守恒、priceDate/valuationDate/source、warnings保留 |

风险evaluate写观察元数据，配置evaluate和preview为财务只读计算；二者不创建订单/占用/结算。税费、滑点、交易限制、外汇NOT_MODELED须明确展示；未知收益不能显示“没有到止盈线”。不提供“执行调仓/全部卖出/一键止盈”按钮。

### R13 DataReadiness、R14 Settings、R15–R17 Portal/Login

| 页面/操作 | 方法/路径/证据 | 保留契约 |
| --- | --- | --- |
| DataReadiness诊断 | GET `/data-readiness?scope&month`（DataReadiness）；[DataReadiness.vue](../web/pc-app/src/views/DataReadiness.vue) 第31–58行、[api](../web/shared/src/api/dataReadiness.ts) | PERSONAL/FAMILY管理员、合法月1000–9998；READY/PARTIAL/UNAVAILABLE/UNKNOWN，checkedAt、各来源dataTime/reason/nextStep。SCHEMA固定UNKNOWN，页面不能实时验证结构；复制仅脱敏问题摘要 |
| Settings资料/密码 | GET `/users/me`、PUT `/users/me`、POST `/users/change-password`；[Settings.vue](../web/pc-app/src/views/Settings.vue) 第146–193行、[user API](../web/shared/src/api/user.ts) | nickname/email/phone；原密码/新密码/重复校验，保存后刷新用户；密码字段成功清空，密码/token不写外观偏好、不写日志 |
| Settings家庭成员 | GET `/families/members`；POST同路径、DELETE `/{userId}`、PUT `/{userId}/role`；[family API](../web/shared/src/api/family.ts) | 成员角色来自真实API，移除有确认；后台assertAdmin为准。GET/POST `/families`为shared能力，当前Settings没有创建家庭流程，不能把新“建立家庭”按钮写成已迁移能力 |
| Settings外观（设计） | 无业务API | 新外观仅第1.2节偏好，不修改认证存储、家庭角色、账户或预算 |
| Portal目录/退出 | GET `/users/me`（store未加载时）；POST `/auth/logout`并清token；[Portal.vue](../web/pc-app/src/views/Portal.vue) 第149–163行 | 保留真实站点href、原子地址、返回登录；不是独立本地账号或业务数据集 |
| Login登录/注册 | POST `/auth/login`、POST `/auth/register`；[Login.vue](../web/pc-app/src/views/Login.vue) 第65–101行 | AuthRequest/AuthResponse，表单校验、loading、真实错误、redirect和总览回跳保留；不可填原型测试凭据 |
| PortalLogin登录 | POST `/auth/login`；[PortalLogin.vue](../web/pc-app/src/views/PortalLogin.vue) 第51–68行 | 同userStore；redirect仅当前代码中的以`/`开头字符串，否则`/portal`。不能声称已实现更严格的站内白名单，也不引入独立注册/本地登录 |

## 5. 核心组件和不可压缩的操作链

| 现有组件/模型 | 页面/职责 | 改版必须保留 |
| --- | --- | --- |
| UnifiedEntryModal | MainLayout/Ledger/Orders等统一记账 | 支出/收入/转账、投资买卖/申赎、MMF快速买入、托管划转、退款及已有手工分录场景；MMF信封份额转移还需读取并PUT更新固定分配额，不只是POST流水。各分支不同API和精度，不合为“自动记一笔” |
| QuickEntryModal、NewOrderModal | 快速收支、独立登记订单 | 金额/支付线/日期、来源账户与真实产品，校验/忙碌/取消/服务端错误 |
| RefundModal、ReimburseModal | 原单退款与报销 | 原单detail、remaining、关联ID、到账账户与日期，保留人工提交 |
| SettlementConfirmModal | Settlements | 输入修改清preview，previewSupported检查，二次确认与freshPreviewToken |
| InitialHoldingImportModal、HoldingDetailModal | Holdings | 手工导入校验与有副作用提交；图表/指标/流水真实请求，图表错误各自可见 |
| FinanceRadar＋financeRadarModel | Dashboard | 真实雷达/来源/旧快照错误标记、只读跳转 |
| MonthlyBudgetGlance＋monthlyBudgetGlanceModel | Dashboard | 手动读取本月，计划/实际、无计划与0区别、取消/超时/身份清理 |
| GoalProgressGlance＋goalProgressGlanceModel | Dashboard | 手动读取、目标上限、有效日期/安全原因、进度与截止的未知状态 |
| BudgetSixMonthReview＋budgetReviewModel/budgetReviewCsv | GoalBudgetCenter | 分页/并发/取消上限，来源与六个月缺项、CSV安全转义/清理 |
| GoalForecastWorkbench＋goalForecastModel | GoalBudgetCenter | 只读单/多目标情景、共享结余约束、数学假设与0%基线 |
| ResearchWorkbench | StrategyLab | 候选证据、方案编辑/归档、回测忙碌/不确定、关联历史/错误 |
| todayTodoModel、pendingSettlementListModel、dashboardKpiTruthModel | Dashboard | 三条独立加载链、真0/未知/部分、不安全导航拒绝、并发/身份失效 |
| goalBudgetModel、riskWatchModel、allocationPolicyModel、dataReadinessModel | 对应中心 | 表单校验、后端枚举、陈旧/未知/证据文案、缺项与冲突错误 |

### 5.1 草稿链

人工输入/AI解析 → 保存DRAFT → 人工补齐真实账户/产品 → preview → 展示缺项/影响/警告且 confirmSupported=true → 用户二次确认 → confirm → 按后端返回结果重读。编辑、选中草稿改变、身份改变应废弃旧预览。sourceType/sourceRef强幂等、CONFIRMED重复确认返回已存在结果、IGNORED不能直接确认、失败回滚保留DRAFT，不由界面制造新来源规避幂等。草稿确认自身无客户端令牌，服务端重新构建新鲜预览。

### 5.2 人工结算链

读取PENDING订单和真实资金线 → 人工填写 confirmDate/navDate/confirmNav/confirmShares/confirmAmount/confirmFee → POST preview → 展示 summaryLines/postingsPreview/warnings/blockingReasons → confirmSupported=true且有freshPreviewToken → 二次确认 → 同组输入＋原样令牌提交 **一个** 确认入口 → 读订单/审计结果。

费用 `null/undefined` 表示按后端估算，明确0表示0；净值必须>0，日期与非负金额/份额/费用按后端校验。输入、订单状态/updatedAt、资金来源、账户快照变更令牌失效，需要重新预览，不自动确认。preview不写结算/账本/账户；confirm经事务写一套结算/账本/持仓/状态，重复确认幂等。历史previewDigest不能回放；关闭弹窗、主题切换、页面加载都不能触发提交。

### 5.3 原有手工正式录入链

统一/快速录入、初始持仓导入、余额调整、退款/报销、删流水、取消订单并非全部已有preview API。保留人工表单校验、明确业务对象与影响、既有确认步骤及服务端事务；不能虚构“预览通过”或把AI候选送入这些快捷正式接口。新界面若要求额外影响预览而API没有，则保持原链或显示明确未支持，不能本地估算成后端批准。

## 6. 接口不足、现存差异与按钮门禁

| 编号 | 证据/缺口 | 界面决定与迁移门禁 |
| --- | --- | --- |
| G01 | dashboard API声明GET `/dashboard/asset-allocation`、`/dashboard/performance`，当前DashboardController仅pending-settlements/asset-overview/today-actions | 不接未实现接口、不虚构绩效；保留现有账户/持仓派生与来源标记 |
| G02 | holdingApi.getHoldingDetail声明GET `/holdings/{id}`，当前HoldingController无对应路由 | 复用HoldingDetailModal；不能加声称后端支持的详情请求 |
| G03 | shared账户Query只有ownerType；后端实际scope/memberUserId；HoldingQueryParams同样未完整表达后端范围 | 新页面不扩大家庭/成员选择能力；后续若需完善契约须另有允许shared/backend的授权，不越界修改 |
| G04 | 全量PC vue-tsc既有失败；Dashboard模板引用未定义handleConfirmSettlement，settlementForm的amount/shares与声明不一致；Orders navData类型缺失 | 保留失败证据；相关入口先验证实际绑定再迁移，不把构建当类型通过；本次不修源码 |
| G05 | MainLayout.handleRefresh 第107–145行调用POST `/ledger/recalculate-all-balance-history`；SecurityConfig 第61–62行将重算入口permitAll | 不在新“只读刷新”按钮调用，不在主题/挂载运行；历史重算单独明确为维护写操作，本轮不执行、不伪称管理员保护。恢复旧UI也不能成为执行授权 |
| G06 | Orders status/ID读、Settlements pending、取消订单未见统一owner门禁（第3节） | 不新增全家订单或批量动作；跨角色与越权检查未通过为上线阻断。不得在前端过滤后声称修复后端泄露 |
| G07 | 余额调整shared返回Account但后端Void；CreateOrderRequest未覆盖页面发送的requestedAt等字段；fundingLines.lineType类型诊断 | UI按真实返回/重读工作，不凭假响应刷新余额；字段契约证据保留，后续不能用any掩盖新差异 |
| G08 | Products导入`@wealth-hub/shared/src/api/product`无法被当前类型解析；拖拽排序调用`as any`已有shared方法 | 列为迁移前局部类型门禁；键盘/触屏排序复用已有POST，不能只改内存顺序后提示保存成功 |
| G09 | 没有真实券商执行/自动结算/自动目标划款API | 删除原型交易、跟投、自动调仓、全部结算、自动转账按钮；不以mock或disabled tooltip营造即将成交 |
| G10 | 没有独立财富事件总流、搜索全站业务、财富排名/AI健康分接口 | 近期事件仅复用todo/待结算/雷达；全站搜索或评分入口不迁移；最近流水可用既有GET，不能造事件接口 |
| G11 | 风险解除由后端评估状态；配置preview税费等NOT_MODELED；研究与现金流只是历史/数学情景 | 不提供前端“解除风险/执行计划”财务动作，不隐藏证据不足；配色绿色不等于可操作 |
| G12 | 未读ZIP17项原名/路径；无真实设备/角色/部署新增证据 | 原型逐项登记保留未知；本文全路由源码映射可核查，不能声称原型或生产体验验收完成 |

## 7. 逐页验收矩阵

此表是后续获授权界面改造的验收契约，不是本次已执行的业务/浏览器验收。每页都验证浅/深/蓝/桃、触屏/键盘、窄屏/200%缩放、loading/成功空态/真实0/未知/部分/失败/人工重试、401/403及身份改变；没有某类数据质量字段的页面按其真实DTO验证，不虚构字段。交易验证必须用独立授权的隔离测试数据，不连接生产。

| 路由 | 针对性验收（通用条件之外） | 通过证据/禁止替代 |
| --- | --- | --- |
| `/` | 基路径部署下仍跳dashboard，未登录带redirect | 路由测试，不能改写所有旧URL |
| `/dashboard` | 四项沿用kpiTruth；部分行情不虚假亏损；todo/待结算独立失败；预算/目标手動；最近流水真实GET；结算fresh gate | dashboardKpiTruth/todayTodo/pendingSettlementList/goalProgressGlance/monthlyBudgetGlance/financeRadar测试＋入口绑定核验；不造0 |
| `/ledger` | 各筛选和统计同口径；页码/total；退款/报销remaining；编辑/删除取消不提交 | 请求/DTO契约与关联结果；不能本地数组改账 |
| `/drafts` | DRAFT编辑；改字段清预览；unsupported不可confirm；重复来源/确认幂等；忽略恢复复制与历史 | draft/aiAccounting请求链和隔离后端验收；模拟成功不能证明正式安全 |
| `/orders` | 四类型、多来源/目标、PENDING取消；结算表单实际绑定；跨owner读/取消受控 | 源码类型门禁＋隔离角色测试；任一权限缺口阻断上线 |
| `/settlements` | 列表越权检查、preview失败/过期token、二次确认取消、单次提交；历史OK/WARNING/BROKEN | SettlementConfirmModal契约/后端隔离测试；audit digest不可作令牌 |
| `/accounts` | 真实叶子账户/用途、负债、MMF未分配、费率、调整后重读；ownerType/scope差异 | store/API契约＋角色范围核验；无全家假汇总 |
| `/products` | 场内/场外过滤、费率阶梯、键盘移动和保存失败；类型导入过关 | 请求POST与重新读取顺序；不把内存拖动当持久化 |
| `/holdings` | 缺行情保持未知、已清仓、来源账户、图表各自错误；导入清空只清表单 | 真源GET/人工导入链；不接未实现holding detail |
| `/goal-budget-center` | 生命周期、预算计划预留≠资金占用、PARTIAL已知、6月缺月/CSV、2–8目标共享上限 | goalBudgetCenter/goalForecast/budgetSixMonthReview/budgetReviewCsv测试；全程不出现财务写请求 |
| `/strategy-lab` | 数据集/策略白名单；历史失败、比较2–5、研究候选三态、证据下载错误、归档/参数草稿/冲突与不确定运行 | researchWorkbench/evidenceExport测试＋真实隔离运行证据；不展示原型收益 |
| `/risk-center` | 进入只GET、evaluate手动、未知不解除、事件去重、ack/mute人工 | riskCenter测试＋角色隔离；不能静默修改评估证据 |
| `/rebalance-center` | 区间边界、比例/百分点、UNKNOWN收益、情景守恒、NOT_MODELED | rebalanceCenter测试；无任何建单/执行按钮 |
| `/data-readiness` | 日期/范围、SCHEMA固定UNKNOWN、READY来源可读文案、脱敏摘要复制失败 | dataReadiness测试；不把已部署或READY称为结构实时验证 |
| `/settings` | 资料/密码校验与清理、真实成员角色、无家庭/非管理员；外观切换请求计数为0且业务状态不变 | isolated API权限＋偏好存储拒绝/损坏测试；无密码持久化 |
| `/portal` | 所有真实href/外站rel、current-user失败、登出返回 | 本地导航检查；不访问生产站点代替测试 |
| `/login` | 登录/注册失败可见、双击忙碌、既有token回跳、redirect | mock边界请求检查＋获授权角色人工验收；不提交测试凭据 |
| `/portal-login` | 同auth/store、回跳portal、输入/键盘/错误 | 无独立本地用户；不凭token存在声明身份有效 |

主题专项：对每个已打开的未提交表单/草稿preview/结算preview切换外观，断言业务网络请求0、字段与选择不变、业务token/状态不变；视觉切换后数据质量文字仍可读。主题不得触发重算或行情采集。

## 8. 分阶段迁移与回滚

仅规定顺序，不创建任务、不修改调度。每阶段必须在当时重新获取v2 HEAD、记录测试/类型基线，并先解决所涉及的实际类型、权限与API门禁；不以本次文档代替任何界面交付。

| 阶段 | 可审阅成果 | 进入下一阶段条件 |
| --- | --- | --- |
| S0 契约基线 | 本文、接口缺口/历史错误、旧UI基线截图（后续使用隔离数据） | 本文路径/来源/Markdown核验；明确未完成验收，不能绕过G04/G06 |
| S1 外观基础/导航/登录壳 | 仅PC token样式、导航可达性、主题偏好、Portal/Login外观 | 17具名页面可达；业务请求/状态不随主题变化；键盘/触屏通过 |
| S2 首页与日常记录 | 真实四摘要、事件、预算/目标、最近流水、Ledger/DraftInbox外观 | 真0/UNKNOWN/PARTIAL回归；草稿确认链/手工录入链和首页结算绑定通过 |
| S3 账户/产品/持仓/订单/结算 | 表格/详情/导入/确认统一视觉 | 费用/精度/来源/幂等、最新preview、人工确认；相关越权门禁未解决则停在本阶段 |
| S4 目标/研究/观察/诊断/设置 | 完整元数据与只读证据界面、情景/导出 | 对应既有回归＋新增针对性UI/外观测试；不增加财务执行能力 |
| S5 完整验收 | 全套PC Node、局部类型、全量类型差异、PC build、hook、diff/允许路径，隔离浏览器与角色人工证据 | 零新增诊断、相关门禁解决；全量历史失败仍明确记录。生产发布与真实业务验收另行授权 |

回滚优先恢复已核验的上一版PC制品/源码外观提交，保留原路由、shared DTO与调用链。未来可以用**仅外观**的旧/新壳选择，绝不是演示或业务切换；本次不实现开关。偏好可忽略/删除自身单个外观key，不清token、账本、研究/预算/草稿，不重放confirm、不删除正式记录、不执行SQL、重算或补偿交易。文档自身可通过仅恢复本文的提交回退；上线回滚需部署授权，本文不触发部署。恢复旧壳不消除旧接口门禁，也不意味着应自动点击旧“刷新数据”。

## 9. 验证与本地交付记录

本次只改文档，不新增源码测试文件（超出allowed_paths）。针对性验证是全路由/核心文件覆盖、相对链接/Markdown表格、禁敏感内容与只改指定文件检查；既有PC测试照常运行。没有业务HTTP、真实身份、服务器、数据库或财务记录验证。

构建hook使用 `-MaxFixRounds 0`，防止失败时自动启动修改越界源码的修复流程；成功只stdout/log，保持仓库静默规则。历史失败必须保留，不能描述为“全部类型/业务验收通过”。

| 检查 | 实际结果与证据边界 |
| --- | --- |
| 初始环境尝试 | 未安装依赖时Node 15个测试文件失败（typescript/shared dist缺失）、type-check缺vue-tsc；属于环境准备失败，不算业务基线 |
| 依赖准备 | `npm ci --ignore-scripts --no-audit --no-fund`（web目录）通过，按锁文件安装156包；`npm run build:shared`通过；未改package/lock/shared源码 |
| 改文档前及最终全套PC Node | `node --test web/pc-app/tests/*.test.mjs`：两次均130/130通过，0失败 |
| 全量PC类型基线及复核 | `npm -w wealth-hub-pc-app run type-check`：失败，99条既有诊断，12文件；下表记录位置/错误码。只改本文，前后业务源码完全一致；不能宣称vue-tsc通过 |
| 局部类型 | 用`node web/node_modules/vue-tsc/bin/vue-tsc.js --noEmit -p <config>`逐一跑全部10份`web/pc-app/tests/tsconfig.*.json`，均通过：allocation、budgetReview、dashboardKpiTruth、goalBudget、goalForecast、goalProgressGlance、monthlyBudgetGlance、pendingSettlementList、research、todayTodo |
| 完整编译hook | `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0`通过：backend `mvn -DskipTests package`、web `npm run build`（shared/PC/H5）；日志`.codex-hooks/logs/latest-build.log`。不把跳过测试的Maven package当后端测试通过 |
| hook过程失败证据 | 一次在前轮仍运行时重复启动，因latest-build.log被占用而失败（Remove-Item IOException）；前轮退出0后串行重跑，不修改hook或源码规避 |
| 文档专项 | 路由名17/17覆盖，根容器单列；113个Markdown相对链接均存在，表格列数和密钥模式检查通过；原型槽位17/17标注不迁移；第10节与真实文件枚举逐项比对无遗漏；只改本文检查通过 |
| 未执行 | 浏览器/触屏/屏幕阅读器、真实角色/设备、隔离财务链端到端、生产部署、数据库结构验证：NOT_RUN；不以Node静态/模型测试替代 |
| 本地交付 | 仅本文source commit，不push。AiCore/Hermes自行核对source commit与Delivery Manifest并按goal_only汇总；本文不创建Delivery记录或发消息，不宣称整个第二轮Goal完成 |

全量类型失败的诊断清单如下。位置为当前基线的源码行；源码未修改，可重跑上方命令恢复完整诊断正文。TS6133/TS6196为未使用符号；TS7006为隐式any；TS2339为缺少属性/处理函数；TS2322/TS2345/TS2353/TS2367为赋值/参数/字段/条件类型不匹配；TS2307为导入无法解析。后续界面任务必须比较新增诊断并解决涉及功能的门禁，不能忽略或用本次文档“批准失败”。

| 文件 | 诊断数 | 全部行号:错误码 |
| --- | --- | --- |
| `src/components/InitialHoldingImportModal.vue` | 1 | `47:TS7006` |
| `src/components/NewOrderModal.vue` | 1 | `218:TS6133` |
| `src/components/QuickEntryModal.vue` | 2 | `58:TS6133`、`59:TS6196` |
| `src/components/RefundModal.vue` | 2 | `131:TS6133`、`134:TS2339` |
| `src/components/ReimburseModal.vue` | 2 | `96:TS6133`、`189:TS6133` |
| `src/components/UnifiedEntryModal.vue` | 42 | `77:TS6133`、`132:TS6133`、`194:TS6133`、`267:TS6133`、`289:TS6133`、`473:TS6133`、`585:TS6133`、`771:TS6133`、`913:TS6133`、`1056:TS6133`、`1151:TS6133`、`1177:TS6133`、`1214:TS6133`、`1337:TS6133`、`1376:TS6133`、`1392:TS6133`、`1408:TS6133`、`1683:TS6133`、`1813:TS6133`、`1814:TS6133`、`1817:TS6133`、`1825:TS6133`、`1839:TS6133`、`1840:TS6133`、`1843:TS6133`、`1851:TS6133`、`1865:TS6133`、`1866:TS6133`、`1869:TS6133`、`1877:TS6133`、`2247:TS2322`、`2257:TS2322`、`2564:TS6133`、`2568:TS6133`、`2703:TS6133`、`2708:TS6133`、`2713:TS6133`、`2719:TS6133`、`2734:TS6133`、`3885:TS2353`、`4065:TS2367`、`4103:TS2345` |
| `src/layouts/MainLayout.vue` | 2 | `180:TS6133`、`184:TS6133` |
| `src/router/index.ts` | 1 | `95:TS6133` |
| `src/views/Accounts.vue` | 6 | `829:TS6133`、`835:TS6196`、`1044:TS6133`、`1094:TS6133`、`1094:TS6133`、`1505:TS6133` |
| `src/views/Dashboard.vue` | 27 | `52:TS2339`、`68:TS2339`、`467:TS2339`、`748:TS2339`、`750:TS2339`、`752:TS2339`、`809:TS2339`、`812:TS2339`、`814:TS2339`、`816:TS2339`、`829:TS2339`、`832:TS2339`、`840:TS2339`、`841:TS2339`、`851:TS2339`、`854:TS2339`、`856:TS2339`、`858:TS2339`、`888:TS6133`、`941:TS2339`、`941:TS2339`、`950:TS2339`、`950:TS2339`、`1140:TS2339`、`1141:TS2339`、`1203:TS2339`、`1204:TS2339` |
| `src/views/Orders.vue` | 11 | `297:TS6133`、`427:TS2339`、`428:TS2339`、`437:TS2339`、`438:TS2339`、`451:TS2339`、`452:TS2339`、`747:TS6133`、`860:TS6133`、`1111:TS6133`、`1117:TS6133` |
| `src/views/Products.vue` | 2 | `465:TS2307`、`529:TS6133` |

合计：99条 / 12文件；本次新增源码诊断0（源码无改动），全量类型门禁仍未通过。

## 10. 全量源码核对清单与数量闭环

核对基线共17个View、1个布局、1个router、27个核心组件文件（14 Vue＋13 TS模型/工具）、28个shared API文件（26业务模块＋client＋index）、23个shared types文件（22 DTO模块＋index）、4个store文件（3业务store＋index）。未把原型17侧栏数量与真实17个具名路由混为相同菜单；没有由导航13项漏掉Settlements。

View逐页覆盖见第2/4/7节；27个组件逐项覆盖见第5节（Vue14/14、TS13/13）。shared模块全量对应如下，工具/出口也单列，没有以代表性页面替代全站核对。

| shared API模块（26/26） | 对应types模块/调用契约 | 本文归属 |
| --- | --- | --- |
| [account.ts](../web/shared/src/api/account.ts) | [account](../web/shared/src/types/account.ts)；树/余额/MMF份额 | R06及G03/G07 |
| [aiAccounting.ts](../web/shared/src/api/aiAccounting.ts) | [aiAccounting](../web/shared/src/types/aiAccounting.ts)；AccountingIntent/DraftFromIntent | R03 |
| [allocationPolicy.ts](../web/shared/src/api/allocationPolicy.ts) | [allocationPolicy](../web/shared/src/types/allocationPolicy.ts)；比例/情景 | R12 |
| [auth.ts](../web/shared/src/api/auth.ts) | [user](../web/shared/src/types/user.ts)；AuthRequest/Response | R16/R17及第3节 |
| [backtest.ts](../web/shared/src/api/backtest.ts) | [backtest](../web/shared/src/types/backtest.ts)；history/compare/research/evidence | R10 |
| [brokerFee.ts](../web/shared/src/api/brokerFee.ts) | BrokerFeeConfig在API文件内定义，无独立types文件 | R06 |
| [dashboard.ts](../web/shared/src/api/dashboard.ts) | [dashboard](../web/shared/src/types/dashboard.ts)；AssetOverview等；待结算使用order | R01/G01 |
| [dataReadiness.ts](../web/shared/src/api/dataReadiness.ts) | [dataReadiness](../web/shared/src/types/dataReadiness.ts) | R13 |
| [draft.ts](../web/shared/src/api/draft.ts) | [draft](../web/shared/src/types/draft.ts)；生命周期/预览 | R03/第5节 |
| [family.ts](../web/shared/src/api/family.ts) | user中的Family/FamilyMember | R14；createFamily为shared能力但当前Settings无流程 |
| [financeRadar.ts](../web/shared/src/api/financeRadar.ts) | [financeRadar](../web/shared/src/types/financeRadar.ts) | R01/R11/R12来源 |
| [goalBudget.ts](../web/shared/src/api/goalBudget.ts) | [goalBudget](../web/shared/src/types/goalBudget.ts)；goalApi/budgetApi | R01/R09 |
| [goalForecast.ts](../web/shared/src/api/goalForecast.ts) | [goalForecast](../web/shared/src/types/goalForecast.ts) | R09 |
| [holding.ts](../web/shared/src/api/holding.ts) | [holding](../web/shared/src/types/holding.ts)；InitialHoldingImport/AccountHoldingInfo在API内 | R08/G02/G03 |
| [indicator.ts](../web/shared/src/api/indicator.ts) | [indicator](../web/shared/src/types/indicator.ts) | R08历史指标；GET `/indicators/latest?productId&windowDays`为shared能力，不凭此增交易信号按钮 |
| [ledger.ts](../web/shared/src/api/ledger.ts) | [ledger](../web/shared/src/types/ledger.ts) | R02/第5节；reverse为shared能力、重算见G05 |
| [ledgerStats.ts](../web/shared/src/api/ledgerStats.ts) | [ledgerStats](../web/shared/src/types/ledgerStats.ts) | R02；多值序列化保留 |
| [market.ts](../web/shared/src/api/market.ts) | [market](../web/shared/src/types/market.ts) | R01/R06/R08；另有GET `/market/bars/latest`、`/market/quotes/latest`，均带productId |
| [nav.ts](../web/shared/src/api/nav.ts) | [nav](../web/shared/src/types/nav.ts) | R01/R04/R06/R08；history/latest/by-date |
| [order.ts](../web/shared/src/api/order.ts) | [order](../web/shared/src/types/order.ts) | R04/R05；confirmSettlement实际经orders/{id}/settle |
| [product.ts](../web/shared/src/api/product.ts) | [product](../web/shared/src/types/product.ts)；FundSellFeeTier在API内 | R07 |
| [researchPlan.ts](../web/shared/src/api/researchPlan.ts) | [researchPlan](../web/shared/src/types/researchPlan.ts) | R10 |
| [riskWatch.ts](../web/shared/src/api/riskWatch.ts) | [riskWatch](../web/shared/src/types/riskWatch.ts) | R11 |
| [settlement.ts](../web/shared/src/api/settlement.ts) | order中的SettlementPreview/SettlementAudit/请求类型，无独立settlement types文件 | R05/第5节 |
| [todo.ts](../web/shared/src/api/todo.ts) | [todo](../web/shared/src/types/todo.ts)；TodayTodo | R01 |
| [user.ts](../web/shared/src/api/user.ts) | user中的资料/密码/UserInfo | R14/R15 |

辅助出口：api/client.ts（JWT/错误）、api/index.ts（导出），types/index.ts（导出）；store/account.ts（树/现金叶子/重读）、store/product.ts（字段兼容/错误清空）、store/user.ts（真实认证）、store/index.ts（导出），均在第3节对应。行情/净值/指标 latest 方法仅将404映为null，其他错误继续抛出；缺价格不能映成0。上述API目录是源码静态能力，不是对生产端点做过可用性探测。
