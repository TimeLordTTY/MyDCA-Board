# 财富中枢 PC 流水查询与记一笔体验

- 任务：`task-aiw-r2-12-ledger-entry-experience-20261010`；验收日期：2026-10-11，Asia/Hong_Kong。
- 已先读取 owner-approved inbox 任务、AGENTS.md、AGENT.MD、DOCUMENT_INDEX、CURRENT_DEVELOPMENT_STATE 和上一张持仓交付。`git fetch origin v2` 后，HEAD 与 origin/v2 均为 `32214f68dcc82ace10d845457cc1fccb7242d6e5`，工作树干净；前置持仓提交与本地 manifest 已包含。保持隔离 detached worktree，不移动 v2，不推送。
- 源码和交付文件仅修改 `web/pc-app/**` 与本文。未修改 inbox、shared、Java、SQL、Android、调度或审批；未访问生产数据库、券商或真实账本。测试仅使用内存 mock 与全拦截的本机浏览器 fixture。

## 查询与展示

| 能力 | 实现及真实接口边界 |
| --- | --- |
| 年轻版流水页 | 沿用纸感背景、现有主题变量和表格完整字段；新增页面内「记一笔」、查询范围说明、类型标签、可解释的未知分类 |
| 月份与日期 | 月份转换为完整自然月 startDate/endDate，包括闰年；日期范围仍按本地日历构造，不通过 UTC 截断查询日期。月份和日期范围互斥，重置恢复全部期间 |
| 真实列表查询 | `/ledger/txns` 保持 txnType、startDate、endDate、parentAccountId、accountId、note、page、pageSize 既有参数；服务器分页、total 与 10/20/50/100 页大小保留。不增加作用域、用户、家庭或未支持参数 |
| 分类 | 列表 API 没有 categoryId 参数。当前页分类筛选明确标注「仅当前页」，提供未分类、未知分类选项，不改变服务器 total。全期间分类查询复用统计 API 的 categoryIds，不把本页筛选伪装成全量查询 |
| 统计 | 保留服务器 summary/trend/breakdown/top、按日/周/月及分类/类型/子账户/父账户维度、Top10 和转账开关。统计只跟随日期/账户及其独立类型/分类控件，明确不跟随备注和本页分类；不累加当前页生成金额 |
| 加载与空态 | 流水、账户、统计、详情各自有失败提示与手工读取重试；开始读取即清除旧列表/统计。成功空列表、本页无分类匹配、未知分类、读取失败分开。缺少金额/余额/统计字段显示未知，真实 0 保留 |
| 详情与编辑 | 保留原始分录、币种、账户及余额；无分录有解释。订单关联、已冲销/取消、其他所有者、服务器 canEdit=false 或无编辑表单的历史记录显示只读原因；打开编辑前再读取详情。后端仍是权限事实源，错误文案保留 |
| 生命周期 | 列表、账户、统计、详情读取绑定请求序号及 token/user/family 身份；迟到响应、关闭详情、停用/卸载不恢复旧数据；移除全局刷新监听。账户下拉来自通过门禁的真实账户列表及既有建树函数 |

金额仍沿用既有 API 的元单位、原分录金额/份额/币种与正负号规则，未新增分/元乘除或汇率折算。列表/统计未提供币种质量字段时不能声称跨币种汇总已经换汇；页面说明统计沿用服务器口径。明细保留各分录 currency，未从账户余额推导交易金额。

## 录入与人工边界

1. UnifiedEntryModal 保留业务表单，改为「选类型 → 填详情 → 人工确认」；核对页只显示当前类型相关字段，修改填写内容后必须重新核对。它是用户输入核对，**不是结算影响预览**；场外订单仍需原订单与结算页面的服务器 preview/fresh token/二次 confirm。
2. 支出/收入/金额转账、组合支付仍调用原 createTransaction/updateTransaction 分录接口；组合支付是已有多分录能力，未用 localStorage 或本地虚构余额模拟。场外买卖保留 createOrder，MMF 保留原 quickBuyMoneyMarketFund，转托管保留原正式接口。退款、报销仍复用原 Modal/API。
3. 编辑上下文只能使用 updateTransaction；若原投资组合会走新建订单/新建流水，则拒绝并解释到原业务页面处理，防止把编辑变成新增副本。退款/报销/快速录入增加人工确认弹窗，取消不发请求。
4. 新旧录入页面共用 pending/uncertain 写入门禁；同一表单提交期间冻结控件、阻止连续点击、关闭和返回操作。网络/超时/5xx 表示写结果未知，锁住后续写请求，提示核对流水/订单后重新打开页面；不自动重试财务请求。该锁仅在当前浏览器运行期有效，**不宣称后端幂等或跨浏览器刷新防重**。
5. 核对后身份/表单变化不能提交；UnifiedEntry 在任何最终写接口前再次检查身份及核对快照。某流水已提交但后续配套更新失败时禁止本次表单重提，并提示核对。
6. 金额输入及各正式接口字段保留；本地当前时间改为真实本地日历时间，避免旧 UTC 字符串截断后冒充本地时间。历史发生时间保留服务器字符串，不二次转 UTC。
7. 原逆回购表单代码仅有单条 CASH 分录并注明简化处理；当前入口禁用并解释未提供完整闭环。原余额调整表单仅提示到账户管理，入口禁用并给出对应去向。保留源码及原业务页面，不增加模拟撤销、自动结算或自动记账入口。
8. 成功后刷新既有账户和 data-refresh 事件；账户读取失败仍通知其他视图读取服务器状态。不调用 recalculateAllBalanceHistory，不自行重算余额，不调用 settlement confirm。原手工删除仍由人工警告确认、原 DELETE 和服务器所有权校验处理。

## 验证证据

| 检查 | 结果及边界 |
| --- | --- |
| 编辑前基线 | 安装缺失依赖 `npm ci --ignore-scripts --prefix web` 并构建 shared 后，PC Node 168/168；全量 PC TS 62 条历史诊断。首次 Node 与 shared build 并行时 shared/dist 尚不存在，不能运行；shared build 完成后重新采集成功基线 |
| PC Node 全量 | `node --test web/pc-app/tests/*.test.mjs`：180/180 通过，含新增流水/录入 12 项；测试执行编译后的真实 setup 和真实门禁，不连接后端 |
| 录入回归 | 核对前零写请求、改值后重核对、连续点击只写一次、取消确认、编辑只更新、组合支付原始分录、旧新入口共用锁、网络/500 锁定、403 原错误、身份切换待确认期间零写入 |
| 查询回归 | 月份/闰年/本地日历、原分页查询参数、分类范围/未知分类、空列表/网络/权限失败、统计失败不变零、迟到读请求/详情关闭/身份/停用/卸载、全局监听移除、账户读取失败仍刷新其他视图 |
| 相关 TS | `node web/node_modules/vue-tsc/bin/vue-tsc.js --noEmit -p web/pc-app/tests/tsconfig.ledger.json`：通过；strict 保留，局部配置仅关闭历史 noUnusedLocals/noUnusedParameters。覆盖 Ledger、Unified/Quick/Refund/Reimburse 及两个新模型 |
| 全量 TS | `npm --prefix web/pc-app run type-check`仍失败：62 → 56；去除行列坐标后比较完整诊断多重集合，无新增，减少 6 项。本任务局部修复分类级联 string/number 路径、退款时间字段及现有订单请求的 PC 本地类型描述，不修改 shared DTO 或 wire payload |
| 浏览器 | Playwright CLI + Edge，本机所有业务请求拦截、非本机域名中止；1440/1100/760 宽度、备注服务端查询、0/未知分类、详情/Esc、加载失败清旧记录、手工重试/真空列表、填写金额/真实账户选项/分类、人工作业核对/返回/取消、模拟 500 后锁定。pageerror=0；29 个 fixture 请求，只有人工确认后 1 个被拦截的 POST，金额 12.34 元；预设 500 产生预期 console 网络错误 |
| 构建 hook | `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0`：通过，exit 0；运行 backend `mvn -DskipTests package`、shared/PC/H5 build。后端单元测试未运行；hook 原行为保持，成功只写 stdout/log。日志 `web/pc-app/tests/ledger-hook.log` |
| Git | `git diff --check`通过；本地提交，不 push/部署 |

日志在 `web/pc-app/tests/ledger-*.log`，截图在 `web/pc-app/output/playwright/ledger-*.png`，均为 ignored 本地证据。复现浏览器：PC 目录启动 Vite `--host 127.0.0.1 --port 3110`，使用 `npx --package @playwright/cli playwright-cli -s=ledger open about:blank --browser msedge`，再 `run-code --filename tests/ledgerExperience.browser.js`。只在隔离 fixture 中运行，不在生产或真实登录页面运行。

## 交付与未执行边界

- 本地 manifest：`web/pc-app/delivery/task-aiw-r2-12-ledger-entry-experience-20261010.json`；源提交由包含该文件的 Git commit 解析，避免自引用 SHA。
- 旧版恢复：可对本任务源码提交执行 git revert（仅说明，未执行）。已有功能代码保留，未改后端过账口径。
- 生产连接、数据库迁移、真实记录写入、交易、自动订单/结算/入账、生产部署、推送：**NOT_RUN**。
- 真实 owner/family 角色权限、跨币种后端财务计算、真实断网后写入结果核对、真实场外结算端到端：**NOT_RUN**。mock/编译通过不能替代这些人工验收。
- 外部 AiCore Delivery Manifest 登记：**PENDING_EXTERNAL_CHAIN**；Hermes/企业微信回执：**NOT_VERIFIED**。没有可调用 AiCore/Hermes delivery 工具，外部路径不在 allowed_paths，未越界写 inbox、调度或审批。本地可解析 manifest 留给 AiCore/Hermes 接续 goal_only 汇总，不宣称全轮 Goal 完成或已通知。
