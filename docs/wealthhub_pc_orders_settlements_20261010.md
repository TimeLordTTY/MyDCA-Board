# 财富中枢 PC 订单、待结算与人工确认体验

- 任务：`task-aiw-r2-14-orders-settlements-20261010`；本地验收日期：2026-10-12，Asia/Hong_Kong。
- 已先读取 owner-approved inbox 任务、AGENTS.md、AGENT.MD、DOCUMENT_INDEX、CURRENT_DEVELOPMENT_STATE 与上一张草稿流程交付。执行 `git fetch origin v2` 后，HEAD / origin/v2 都为 `b9d2d4453737e098d9162be44b3004e6f58cc65f`，工作树初始干净。依赖 task-aiw-r2-13 的源码、202 项 Node 回归与本地 LOCAL_VERIFIED manifest 已包含；未越过失败依赖。
- 保留隔离 detached worktree，本地提交，不移动主工作区 v2，不推送。项目编辑仅在 `web/pc-app/**` 与本文；未编辑 inbox、shared、Java、SQL、Android、调度或审批。请求的依赖安装与构建产生 ignored node_modules/dist/target/hook 日志，不修改这些区域的源码或依赖锁文件。

## 页面交付

| 页面 | 保留与改进 |
| --- | --- |
| Orders.vue | 保留原 getOrders/getOrder、新建 NewOrderModal、人工取消与结算入口。增加状态、方向、产品/订单/账户搜索；只统计当前返回记录的笔数，不汇总为资产或成交。展示服务器最新详情、SOURCE/TARGET 资金线、净值与日期；详情加载失败逐笔说明，主列表失败清旧数据 |
| 订单详情 | 抽屉只读查看原金额/份额/预计费用、发起日期、预计净值/确认日、备注、来源与到账资金线、实际结算记录及关联流水；保留产品/渠道、历史记录和关联持仓、审计入口。结算金额/份额/净值/日期/费用编辑统一移到共用结算弹窗，不删除正式 API 能力 |
| Settlements.vue | 待结算卡片按产品/订单和方向筛选，分别说明买入/申购与卖出/赎回阶段；缺失关键下单数值明确提示。读取失败、服务器空结果和筛选无匹配分开，不把失败当作没有待办 |
| 人工结算弹窗 | 打开时重新读取最新订单及资金线；六位净值/份额、实际金额、确认日期、净值日期、费用、备注均保留。净值只能人工点击读取所选日，不用最新净值替代未知历史日；不在浏览器计算正式份额、到账或账本 |
| 预览 | 展示原服务器 summaryLines、computedShares/computedAmount/confirmFee、逐账户分录及 warnings/blockingReasons；UNKNOWN/null 不转为 0。费用留空表示服务器估算，明确 0 保持 0 |
| 审计 | 保留原 getHistory/getAudit，增加 OK/WARNING/BROKEN 筛选、确认人/时间/日期/净值/金额/份额/费用、现金/持仓影响、资金线、流水关联、分录、预览摘要与原因。摘要仅展示，绝不作为令牌；历史关联缺失、不一致及未知值均保留，不修复 |
| 视觉与可访问性 | 沿用本轮纸感主题、衬线标题和既有主题变量；订单表、双/单列复核卡、深色和 1440/1100/760 宽度适配；错误 role=alert、加载 role=status、筛选 aria-pressed、键盘焦点。弹窗内部滚动，标题/操作按钮保持可达 |
| 生命周期 | 列表、详情、审计、预览、净值读取绑定请求序号与 token/user/family 身份；关闭、编辑、刷新、身份切换、停用/卸载丢弃迟到响应并清除旧数据。初始 user hydration 重新读取当前身份，深链接只打开复核/审计，不自动 preview/confirm；移除全局刷新监听 |

没有新增原型 localStorage 记账、模拟持仓、批量结算、自动建单、后台确认或交易渠道。原 NewOrderModal 保留明确人工新建入口，未增加自动调用。后端固定关闭 isManualOverride，现有前端开关不能改变结算行为，因此保留为禁用并明确说明，不提供绕过能力。

## 逐字核验安全链

任务原文：**“preview 只读；确认须主人再次明确动作并携带后端 freshPreviewToken；任何影响金额/日期/份额/账户的编辑会使旧预览失效；后端拒绝/重复/409/401/403应如实展示。”**

| 要求 | 当前代码证据与回归 |
| --- | --- |
| preview 只读 | SettlementConfirmModal.handlePreview 仅调用原 settlementApi.previewSettlement。共享 API 对应 POST /settlements/preview；只读服务契约在 SettlementService.previewSettlement/runSettlement(apply=false) 已存在。本任务只静态读取后端，26 项新增回归证明打开页面、表单、preview 不会调用 confirm |
| 主人再次明确动作 | handleSubmit 在等待 ElMessageBox.confirm 前设置 submitting；展示原 buildSettlementPreviewText/Title 的现金、持仓、费用影响，按钮“我已核对，确认结算”。取消消耗旧预览，不写入。表单修改、关闭、身份切换或卸载发生于弹窗等待期间会再次检查并阻止旧提交 |
| 后端 freshPreviewToken | canConfirm 要求当前 PENDING 订单、ID/方向/状态匹配、confirmSupported=true 和非空令牌；handleSubmit 原样携带 fresh.freshPreviewToken 与原预览输入，调用原 orderApi.confirmSettlement，即 POST /orders/{id}/settle。无本地 token、摘要代用、请求字段重算或自动重试 |
| 输入与账户变化失效 | deep/flush:sync 表单 watcher 清除预览；请求期间编辑后返回的旧结果也被 revision 拦截。金额、份额、日期、净值、费用、备注均覆盖；订单/资金线/账户 props 快照变化重读最新详情，身份变化清会话。此页面不提供编辑账户资金线；账户或持仓在其他会话变化仍由后端令牌指纹重新核验 |
| 拒绝 / 重复 / HTTP 状态 | actionError/listError/historyError/auditError 保留服务器 message/error 与 HTTP 状态；409 提示重读与重预览；401/403 说明会话/权限，沿用 shared 拦截器原有登录跳转。重复拒绝如实显示；若原后端幂等返回已存在结果，只写“服务器已返回结算成功”，不宣称新建第二笔 |
| 并发及未知提交结果 | 预览并发只发一次；二次确认等待与写入期间重复点击只产生一次弹窗/一次写入。网络、408、5xx 后按身份+订单在组件内存锁定重提，要求刷新订单及核对历史，无自动确认重试；跨重载/跨设备的幂等仍由原后端保证，不宣称新增能力 |

BUY/SUBSCRIPTION 的 PENDING 阶段已有付款和 RECEIVABLE 应收；结算清理应收、形成 POSITION/关联账户影响并处理费用，不重复扣同一笔下单现金。SELL/REDEMPTION 的 PENDING 阶段仅 SOURCE/TARGET 份额占用，不生成账本、不改现金或持仓；结算后才产生到账现金、持仓减少及费用。以上依据当前 OrderService/SettlementService 真实实现，页面按方向分别解释，不使用原型本地结算规则。

## 验证证据

| 检查 | 实际结果与边界 |
| --- | --- |
| 环境 / 基线 | 工作树初始没有 node_modules，第一次 type-check 无法启动。按原锁文件执行 npm ci --ignore-scripts --no-audit --no-fund，再执行请求的 shared build 后采集有效基线：PC Node 202/202，PC 全量 TS 56 条历史诊断。没有更改 package.json/lock 或 shared 源码 |
| PC Node 全量 | `node --test web/pc-app/tests/*.test.mjs`：最终 **228/228 通过**，包含 ordersSettlements 26 项；真实 Vue setup 编译执行，三个模板编译无错误 |
| 回归范围 | 四方向正常人工确认/原始输入与服务器令牌、0/undefined 费用、缺金额/未知净值/无效日期与非有限值、PENDING 状态异常、missing token/不支持/预览不匹配、迟到预览/净值、金额/日期/份额/账户编辑、并发多次点击、取消二次确认、400/409/401/403 后端拒绝/重复、网络/500/408 锁定、关联持仓/订单/审计、读取失败清旧数据、家庭/身份切换、生命周期与初始深链接 |
| 相关 TS | `node web/node_modules/vue-tsc/bin/vue-tsc.js --noEmit -p web/pc-app/tests/tsconfig.ordersSettlements.json`：通过；保持 strict，仅关闭历史 unused 检查（保留 NewOrderModal 原有 acc 诊断）。任务修改文件在全量默认 unused 检查也没有新增诊断 |
| 全量 TS | `npm --prefix web/pc-app run type-check`：仍失败，**56 → 45**；去除位置后比较诊断多重集合，无新增，移除 Orders 的 11 条旧诊断。其余 45 条在既有未修改组件/路由/首页，不声称全量 TS 通过 |
| 隔离浏览器 | Playwright CLI + Edge，所有业务请求拦截，外部域名中止。1440/1100/760 无页面溢出；订单状态筛选、资金详情、所选日净值、服务器预览、输入编辑失效、取消二次确认、409 清令牌、一次明确确认后成功、赎回缺金额阻断、WARNING 审计、深色、500 清旧行与人工重试通过。**pageerror=0，最终 49 次 fixture 请求，4 次 preview，2 次 intercepted confirm（1 次 409 + 1 次成功），0 次真实后端请求**。两条预期网络 console error 来自 409/500；401/403 的服务器原因与身份隔离由 Node 模拟覆盖 |
| 构建 hook | `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0`：通过，exit 0；backend mvn -DskipTests package 与 shared/PC/H5 build 成功。**后端单元测试未运行**。未修改 hook，遵循仓库静默成功规则，无 Beep/系统通知 |
| Git | `git diff --check` 通过；源码、测试、CSS、专项文档及 manifest 仅允许路径。本地提交，不推送、不部署 |

日志：`web/pc-app/tests/orders-*.log`；截图：`web/pc-app/output/playwright/orders-preview.png`、`settlements-{1440,1100,760,dark}.png`，均为 ignored 本地证据。

浏览器复现：PC 目录启动 Vite `--host 127.0.0.1 --port 3114`，运行 `npx --package @playwright/cli playwright-cli -s=orders14 open about:blank --browser msedge`，再 `run-code --filename tests/ordersSettlements.browser.js`。必须在该独立 fixture 会话运行，不在真实登录或生产页面运行。

## 本地交付与外部边界

- 本地 AiCore Delivery Manifest：`web/pc-app/delivery/task-aiw-r2-14-orders-settlements-20261010.json`；source commit 由包含 manifest 的本地 Git commit 解析，避免自引用 SHA。
- 旧版保留于父提交 `b9d2d445`；可通过本次提交的 git revert 恢复旧版（仅说明，未执行）。
- 生产数据库、迁移、真实财务记录/账本、券商/交易、自动创建订单/结算/入账、部署、push：**NOT_RUN**。
- 真实 owner/family 角色、真实断网后的账本核对、人工结算正式财务端到端：**NOT_RUN**；mock 与构建不能代替真实财务验收。
- 外部 AiCore Manifest 登记：**PENDING_EXTERNAL_CHAIN**；Hermes/企业微信收据：**NOT_VERIFIED**。当前工具无可调用 AiCore/Hermes delivery 能力，外部路径不在 allowed_paths；未越界修改调度/审批/inbox 或创建后续任务。由 AiCore/Hermes 接续第二轮 Goal 的 goal_only 汇总，不宣称全轮 Goal 完成或已通知。
