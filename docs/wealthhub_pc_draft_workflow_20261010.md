# 财富中枢 PC 草稿箱与人工复核流程

- 任务：`task-aiw-r2-13-draft-workflow-20261010`；本地验收日期：2026-10-12，Asia/Hong_Kong。
- 已先读取 inbox 授权文件、AGENTS.md、AGENT.MD、DOCUMENT_INDEX、CURRENT_DEVELOPMENT_STATE 和上一张账本体验交付。执行 `git fetch origin v2` 后，HEAD 与 origin/v2 均为 `8f18d6a726bab65db60dbafbbcbee7ecbacef623`，工作树干净；依赖 `task-aiw-r2-12-ledger-entry-experience-20261010` 的源码与本地 manifest 已包含。继续使用隔离 detached worktree，不移动 v2，不推送。
- 项目文件仅修改 `web/pc-app/**` 与本文。未修改 inbox、shared、Java、SQL、Android、调度、审批。请求的构建会生成被忽略的构建制品和 hook 日志；未连接生产数据库、后端业务服务、券商或真实账本。

## 草稿箱体验

| 能力 | 实现及范围 |
| --- | --- |
| 卡片与状态 | 沿用本轮纸感主题、衬线标题与主题变量，三/二/单列响应式卡片。显示状态、方向、候选金额或份额、原文、字段缺口、来源标识和更新时间；不累加草稿金额为余额或资产 |
| 筛选与分页 | 状态继续通过原 `listDrafts({status,page,pageSize:50})` 读取。搜索、来源、方向、最近 7/30 天仅筛选当前页，有明确文案。接口无 total，按返回数量提供上一/下一页，不伪造总条数 |
| 详情抽屉 | 打开时通过原 getDraft 读取最新记录，保留来源、原文、解析 JSON、生命周期历史、关联订单与结算审计入口。已忽略记录显示原因，已确认记录显示正式流水或内部订单标识；状态变化时关闭编辑 |
| 字段弹窗 | 保留七种方向及原有备注、来源提示、产品提示、预计净值/确认日。保存只调用 updateDraft；「保存并预览」仍只更新草稿及请求影响预览，不调用 confirm |
| 文本录入 | 独立弹窗，必须先点击 parseText，再手动点击 draftFromIntent。展示类型、金额、份额、账户/产品/持仓来源/到账提示及缺失字段。修改文本或解析失败清除旧候选；生成期间阻止重复点击 |
| 历史状态 | DRAFT 才可编辑、预览、忽略和确认。IGNORED 保留人工恢复；CONFIRMED 保留人工复制与关联审计。恢复/复制仍需重新预览和二次确认，复制使用原 copyConfirmed API |
| 空态与失败 | 加载、读取失败、服务器空页、本页筛选无匹配分开。列表失败清除旧记录，权限/来源冲突/字段/份额错误保留服务器原因；选项失败关闭保存能力并提供手动重试 |
| 生命周期 | 列表、详情、预览、历史、选项、持仓、文本解析绑定请求序号及 token/user/family 身份。关闭详情、身份变化、停用/卸载、重叠请求不恢复旧结果；移除全局刷新监听。首次 user 资料加载后补发当前身份只读列表请求，后续身份/家庭变化要求手动刷新 |

账户和产品选项由既有只读 API 加载，沿用 shared 的 buildAccountTree/getLeafAccounts。页面持有通过请求门禁的选项快照，避免共享 store 中迟到响应覆盖当前表单。没有修改共享 DTO、JWT、服务器作用域参数或权限规则；真实可见范围与最终写入权限仍由服务器决定。

## 字段与确认契约

| 方向 | 原有字段及复核限制 | 确认的原有后端影响 |
| --- | --- | --- |
| EXPENSE / INCOME | 正数金额、启用真实叶子账户；支出选项遵循 SPENDABLE 用途。金额/份额拒绝空值、0、负数、非数字和非有限值 | 正式收支流水 |
| TRANSFER | 正数金额、不同的转出/转入账户、相同币种，双账户影响分别展示 | 一笔正式转账流水 |
| BUY / SUBSCRIPTION | 必须明确选择启用真实产品和单一资金账户；资金来源为 REAL 叶子 INVESTABLE，BOND_REPO 额外允许 RESERVED；币种匹配 | 创建内部 PENDING 订单，并生成付款与待结算应收账本；不自动成交或结算 |
| SELL / REDEMPTION | 真实产品、该产品的真实持仓来源、正数份额、同币种启用 REAL 叶子到账账户；禁止 POSITION/VIRTUAL/父账户作为到账账户 | 只创建内部 PENDING 记录与待处理份额占用，不立即生成账本、不减少持仓、不增加到账余额 |

可用金额、可用份额（已扣除 PENDING 占用）、预计剩余份额及影响对象均展示服务器预览结果，不从本地账户/持仓总数推算可用值。未知数值显示缺失，未知布尔影响显示「未知」，真实 0 保留。保留既有 API 金额单位，没有分/元换算、汇率折算或 localStorage 财务改写。

1. 仅当前 DRAFT、当前草稿的 confirmSupported=true 预览可以进入确认。点击确认时立即锁住本次操作，先再次调用原 previewDraft，成功且支持后才展示明确二次确认弹窗；取消会消耗本页预览资格。
2. 主人明确同意弹窗后，再检查身份、草稿状态、抽屉及当前预览仍是本次 fresh 结果，才调用原 `confirmDraft(draftId)`。二次确认等待期间或提交期间的连续点击不会重复弹窗/提交。
3. **草稿 API 没有 previewToken 参数。** shared DraftPreview 和 confirmDraft，以及 DraftLedgerEntryService.confirmDraft 的现有代码证明：草稿 confirm 接收 ID，后端锁定记录、重新计算 fresh preview、检查 confirmSupported，并沿用原幂等语义。本页不创造或传递新令牌，不借用 settlement 的令牌接口。所谓清除旧令牌在此落实为清除旧预览及确认资格；结算令牌/确认流程完全未修改。
4. 编辑、保存、忽略/恢复/复制、刷新、取消确认、预览/确认失败、身份变化和离开页面均使旧预览失效。不支持的新预览原因保留在页面，不能沿用上一次的可确认结果。
5. 网络/超时/5xx 的 confirm 结果可能未知，当前组件按草稿 ID 锁住重提，提示刷新草稿并核对关联流水/订单。该锁只在组件运行期有效，不宣称跨刷新、跨设备或新的后端幂等能力；无自动确认重试。
6. 权限/业务失败保留服务器理由并清除旧预览。重复来源返回已有 CONFIRMED/IGNORED 草稿时保留其真实状态，不伪装成新 DRAFT。没有批量确认、静默确认、自动结算或自动正式入账。
7. 投资成功文案显示内部订单仍需人工结算；SELL/REDEMPTION 不显示「已正式记账」。成功后仅通过既有 data-refresh 事件通知读取，不调用历史余额重算。

## 验证证据

| 检查 | 实际结果与边界 |
| --- | --- |
| 编辑前基线 | 既有 shared/dist 首次被 Node 读取时出现目录 import 解析错误。执行请求的 shared build 后重新采集有效基线：PC Node 180/180，全量 PC TS 56 条历史诊断。没有修改共享源码或依赖清单 |
| PC Node 全量 | `node --test web/pc-app/tests/*.test.mjs`：202/202 通过。新增 draftWorkflow 22 项，编译并执行真实 Vue setup 与请求门禁，模板编译无错误 |
| 新增回归 | 七方向字段补全和原 DTO；异常金额/份额；转账同账户/币种；投资用途/真实产品；持仓/到账限制与迟到持仓；来源冲突；保存/文本生成不 confirm；fresh preview 失败/不支持；二次确认取消、防重、身份/家庭切换；离线/500 锁定、403 原因；历史只读及人工忽略/恢复/复制；旧读请求、关闭抽屉、停用/卸载；初始用户 hydration |
| 相关 TS | `node web/node_modules/vue-tsc/bin/vue-tsc.js --noEmit -p web/pc-app/tests/tsconfig.draftWorkflow.json`：通过。保留 strict；局部配置只关闭原 noUnusedLocals/noUnusedParameters，覆盖 DraftInbox 与复用的 managementViewModel |
| 全量 TS | `npm --prefix web/pc-app run type-check`：仍失败，基线 56 → 最终 56。去除行列位置后比较完整诊断多重集合，无新增。不能声称全量 TS 通过 |
| 浏览器 | Playwright CLI + Edge，本机全部业务请求拦截、外部域名中止。1440/1100/760 宽度无页面溢出；字段弹窗、非法金额、真实账户选项、保存并预览、fresh 二次预览、取消确认、409 失效清预览、一次明确确认、已确认/忽略只读、SELL 可用份额、深色复核、列表 500 清旧记录与手动重试、文本解析/生成。pageerror=0；26 次 fixture 请求，1 次草稿 PUT、1 次明确二次确认后被拦截的 confirm。预设 409/500 产生 2 条预期网络 console error；403 权限由 Node 模拟覆盖，真实 shared 拦截器仍按既有语义跳转登录 |
| 构建 hook | `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0`：通过，exit 0，backend `mvn -DskipTests package` 和 shared/PC/H5 build 均成功。后端单元测试未执行。hook 未修改，成功只写 stdout/log，无声音/系统弹窗 |
| Git | `git diff --check`通过。仅允许路径的源码/测试/文档/manifest，本地提交，不推送、不部署 |

日志：`web/pc-app/tests/draft-*.log`；截图：`web/pc-app/output/playwright/draft-*.png`；均为 ignored 本地证据。复现浏览器时，在 PC 目录启动 Vite `--host 127.0.0.1 --port 3113`，运行 `npx --package @playwright/cli playwright-cli -s=draft13 open about:blank --browser msedge`，再 `run-code --filename tests/draftWorkflow.browser.js`。只运行隔离 fixture，不在真实登录或生产页面运行。

## 交付与未执行边界

- 本地 AiCore Delivery Manifest：`web/pc-app/delivery/task-aiw-r2-13-draft-workflow-20261010.json`，source commit 通过包含该文件的本地 Git commit 解析，避免自引用 SHA。
- 旧版保留在父提交 `8f18d6a`。可对本任务源码提交执行 git revert 恢复原版（这里只说明，未执行）；原后端、共享 API、幂等、账本与结算口径保持。
- 生产数据库、迁移、真实财务记录/账本、券商/交易、自动建单/结算/入账、部署、推送：**NOT_RUN**。
- 真实 owner/family 权限、真实断网后财务结果核对、人工结算和正式财务端到端：**NOT_RUN**。mock/构建不是生产验收证据。
- 外部 AiCore Delivery Manifest 登记：**PENDING_EXTERNAL_CHAIN**；Hermes/企业微信收据：**NOT_VERIFIED**。当前没有可调用 AiCore/Hermes delivery 工具，外部路径不在 allowed_paths；未越界修改 inbox、调度或审批。本地 manifest 供 AiCore/Hermes 接续 goal_only 汇总，不宣称全轮 Goal 完成或已通知。
