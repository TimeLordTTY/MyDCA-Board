# 财富中枢 PC 首页重构交付清单

- 任务：`task-aiw-r2-09-home-rebuild-20261010`；日期：2026-10-11（Asia/Hong_Kong）。
- 执行前读取 inbox 任务、AGENTS.md、AGENT.MD、DOCUMENT_INDEX 与 CURRENT_DEVELOPMENT_STATE；`git fetch origin v2` 后 HEAD / origin/v2 均为 `e88c3cd43833fd0dadc87224550925f0e8ae1d0f`，工作树干净，已包含前置导航壳交付。
- 修改范围只有 `web/pc-app/**` 与本文。独立本地 source commit，不推送、不部署、不修改 inbox、调度、审批或后续任务；不创建 Goal。
- 未连接数据库或生产业务 API，未读取/修改真实财务记录。所有交互验证使用本地离线夹具和 mock API。编译 hook 的后端步骤为 `mvn -DskipTests package`，不代表后端单元测试通过。

## 首页信息与真实来源

首页顺序：四大 KPI → 每日财富雷达概览 → 今日待办 → 本月预算/资产目标 → 资产结构/今日建议及核心持仓 → 最近流水 → 待结算清单。常用入口进入原草稿箱、订单与结算、流水页；原全局统一记账、刷新行情、历史余额重算入口在 MainLayout 中原样保留。

| 区域 | 契约与展示 | 保留的质量/人工边界 |
| --- | --- | --- |
| 净资产/可用资金/持仓市值/负债 | 原 `dashboardKpiTruthModel` 与 shared GET；现金 + 真实行情估值 − 负债，可用资金仍扣原账户占用 | 金额口径不变；已知0、UNKNOWN、PARTIAL 不混淆；非实时提示、读取时间、手动重试及展开口径解释仍在；正常态技术说明收进详情，缺来源显示明确解释 |
| 财富雷达 | 原 financeRadar GET 与所有原检查项/目标路由 | 概览统计待核查检查项，包含未知，不伪装成交易笔数；默认最多3个普通优先项，但 BROKEN 全部展示；行情与指标取最高严重程度，已知对账异常不被另一未知计数遮蔽；紧急原始 warning 不被去重隐藏 |
| 雷达证据 | 展开显示原完整卡片、所有 warning code/status/message 和本次完整只读 DTO | 网络失败保留并标明旧快照，403/401 清除旧数据；跨午夜每分钟检查日期，明确过期；账号/家庭/路由/停用失效旧响应，恢复页面不隐式重新请求 |
| 本月预算/资产目标 | 原 MonthlyBudgetGlance、GoalProgressGlance 与既有 shared API | 保留手动加载、作用域与币种筛选、取消预算加载、超时、错误重试、无配置与未加载分离、部分数据和权限处理；没有新自动加载/转账/资金预留；预算读取上限说明可展开 |
| 今日待办/待结算 | 原独立 loaders 和路由 | 未知与真实空列表分离，原草稿导航、待结算重试、详情编辑、人工结算链保留；草稿统计不冒充全部交易；草稿入口改为键盘可操作 button |
| 今日建议/核心持仓 | 原建议 GET；持仓与 KPI 共用请求内 snapshot | 建议区分未加载/加载/失败/成功空列表，独立手动重试，过期响应不回填；原 SETTLE_ORDER 处理器不变。持仓缺行情不再按0估值/虚报亏损，未知项优先核查，保留查看全部持仓 |
| 资产结构 | 新纯展示 AssetStructureGlance；现金/持仓/负债与 KPI 同批来源 | 删除依赖可变 store 且把缺行情当0的配比图；以来源金额显示现金与持仓，负债单列，不生成模拟比例、不猜产品类别占比、不换汇或预测 |
| 最近流水 | 新 RecentLedgerGlance 调用现有 `ledgerApi.getTransactions({ page: 1, pageSize: 5 })` | 手动读取、最多5条、账号授权由原后端执行；服务端现有 Mapper 按 requested_at DESC；DTO 没有金额/币种，故只显示类型/发起时间/备注/标识并导航到原流水页，不借未声明的 summaryAmount 推算金额 |

样式复用前置外观令牌、暖色底/装饰色、大数值与圆角布局，保留浅色/深色/系统/旧版配色；取消对已迁移雷达的深色强制白底兼容覆盖。宽度不足时四卡降为两列/一列，预算目标和详情区依次降为一列；表格保留横向滚动。没有引入模拟 localStorage 数据、新后端契约或演示交易按钮。

## 人工操作与恢复

- 所有原订单编辑、金额/份额/净值/日期输入、结算校验、preview、confirmSupported 阻断、二次确认、freshPreviewToken 和错误处理函数逐字保留。修复原待结算按钮引用未定义的 `handleConfirmSettlement`：仅接回 `openOrderSettlement` 原编辑器；没有直接确认或自动入账。
- 为原已有动态 `settlementForm.amount/shares` 补充可选类型声明，不更改默认值或计算。原 `calculateSettlementFromForm` 未使用诊断仍属于历史基线，未借本任务重写计算。
- 原统一记账、订单管理、流水编辑/退款/报销/撤销、结算审计/历史恢复页面未修改，导航仍指向原页面和权限守卫。离线模拟不代表真实角色或真实金融操作验收。
- 旧版配色继续在设置中可选；恢复完整旧首页应在审阅后 revert 本任务独立 source commit，无数据库迁移或财务记录回滚。本任务没有自动执行回退。

## 验证与门禁证据

| 检查 | 结果 |
| --- | --- |
| 依赖与有效基线 | `npm ci --ignore-scripts --prefix web`、shared build；安装结束前一次 shared build 因缺 tsc 失败，安装完成后重跑通过，不算代码诊断 |
| 变更前 PC Node | 140 / 140 通过 |
| 最终全套 PC Node | `node --test web/pc-app/tests/*.test.mjs`，148 / 148 通过 |
| 局部类型 | `node web/node_modules/vue-tsc/bin/vue-tsc.js --noEmit -p web/pc-app/tests/tsconfig.dashboardHome.json` 通过；覆盖新增模型/速览及雷达、预算、目标组件 |
| 全量类型历史对比 | 原版97项、最终71项，均退出2；去除文件行号按诊断多重集比较无新增，减少26项原 Dashboard 编辑字段/缺处理器错误；仍有历史未使用函数、旧页面 DTO 诊断，不宣称全量类型通过 |
| PC build | `npm run build --prefix web/pc-app` 通过；既有大 chunk 提示保留 |
| 全仓 hook | `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0` 通过；后端打包及 shared/PC/mobile build，不改后端。保持现有非侵入 stdout/log 成功行为 |
| 差异检查 | `git diff --check` 通过；所有提交文件均在 allowed_paths |
| 浏览器/截图/生产 | NOT_RUN；未启动真实业务页、未截图、未宣称浏览器视觉或真实角色验收，不部署 |

新增 `dashboardHome.test.mjs` 通过 Vue SFC 编译和离线 setup 执行，覆盖已知0/未知/部分行情/负现金、30个普通雷达项收拢/12个紧急项全部显露、午夜过期、身份切换与停用旧响应、网络旧快照/403清除、流水手动读取/空态/权限/重试/并发、建议空态/错误/旧请求，以及 BUY/SUBSCRIPTION/SELL/REDEMPTION 四类原编辑入口与 preview 阻断、用户取消、token 确认顺序。现有预算/目标/待办/KPI/结算列表测试继续覆盖手动加载、取消/超时、分页、部分数据、并发、权限失效及恢复边界。

更新原两项操作链对照测试，仍比较全部人工结算与建议处理函数，只排除本次替换的纯图表函数；固定前置 source SHA，提交后可继续复现。原 KPI 模板测试边界随首屏移动，继续验证四卡不含模拟比例。首次夹具失败（Vue reactive 对象严格引用比较与缺 window 清理桩）已修正，首轮断言范围/类型新增问题均已修复并重跑；无遗留新增失败。

本地忽略日志在 `web/pc-app/tests/home-*.log`，不提交构建产物或日志。交付 source 包含 Dashboard、雷达/质量模型、预算/目标样式、两个展示速览及一个流水读取模型、针对性测试/类型配置和本文。后续人工可在隔离 mock 环境检查首屏信息密度、主题、窄屏、键盘/焦点和雷达证据展开；真实权限/交易/金融操作验收须独立授权。正式 Goal 汇总与通知由 AiCore/Hermes 负责，本任务不宣称整个第二轮 Goal 完成。
