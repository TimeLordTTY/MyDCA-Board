# 财富中枢 PC 持仓、估值与收益视图交付

- 任务：`task-aiw-r2-11-holdings-experience-20261010`；核对日期：2026-10-11，Asia/Hong_Kong。
- 执行前读取任务、AGENTS.md、AGENT.MD、DOCUMENT_INDEX、CURRENT_DEVELOPMENT_STATE 与上一张账户/产品交付。`git fetch origin v2` 后 HEAD、origin/v2 均为 `a01a4b64112c866030bed711180594cfbcd9caa1`，工作树干净，前置任务已包含；保留隔离 worktree，不移动 v2、不推送。
- 修改仅在 `web/pc-app/**` 与本文。未修改 inbox、Java、shared、SQL、调度或审批；未访问生产、数据库、券商，未执行迁移、交易、入账、结算或真实财务记录写入。

## 页面与财务口径

| 交付 | 实现与保留行为 |
| --- | --- |
| 持仓观察页 | 延续纸感背景、统一卡片与浅/暗/旧主题；按名称/代码、来源账户、渠道、产品类型筛选；保留清仓开关、浮盈排序及盈亏率；来源账户+产品分别展示，不混合汇总金额 |
| 导航 | Products 原持仓跳转的 productId 在 Holdings 生效；重置可移除路由筛选。持仓“产品档案”打开对应既有只读详情，停用产品仍可查看 |
| 明细 | 展示真实份额、剩余成本、平均成本、服务器市值/浮盈、价格来源、报价时间/净值日期、估值净值证据与读取时间；价格时间、读取时间均不伪称服务器估值时间 |
| 详情 | 保留净值曲线、IOPV/溢价、债券行情、K线、技术指标及交易记录；零指标不再被替换为null；读取失败与无历史证据分开，支持手工重试；关闭、身份/产品/标签切换后的旧响应不再恢复图表或记录，卸载移除监听 |
| 手工行为 | 保留原 InitialHoldingImportModal、导入成功刷新与原 shared import API，只有人工操作可进入；只补充过滤参数的TS类型。未新增下单、直接改份额、本地模拟交易或绕过人工确认的行为 |
| 权限 | GET沿用shared/JWT及服务器默认owner作用域，不增加family/member参数或家庭汇总；token/userId/familyId变化清除页面与详情、关闭导入，迟到响应不能跨身份展示；旧版Map/对象/数组响应保留来源字段 |

`HoldingService.calculateHoldings` 当前使用最新 NAV 计算市值与浮盈；缺 NAV 时会返回默认0，清仓则返回真实0。当前 HoldingInfo 未返回价格、估值日期、质量状态、已实现盈亏或独立收益率。前端因此采取如下展示规则：

1. 有效正净值、非未来且合法的净值日期、已知正份额与服务器正市值共同支持展示服务器金额；缺任一证据则市值/浮盈显示“未知”。金额不由报价乘份额生成，也不重算浮盈。缺剩余成本不展示浮盈。
2. EXCHANGE 的报价独立读取/展示；即便有报价，缺 NAV 也不能拿默认0市值/浮盈冒充估值。为验证真实服务器口径，场内也读 NAV；404由原shared API返回null，不代表行情为零。各产品NAV使用allSettled，单一失败不抹除其他产品成功证据。
3. OTC价格展示对应净值，场内价格展示报价，不把 BOND_REPO 年化报价当基金净值。历史日期保留原值，无“今日/实时估值”承诺。净值证据与持仓请求是独立读取，不能证明是同一原子快照；文案明确“后端未返回估值时间”。
4. 合法负浮盈与0浮盈保留数值与符号；真实0份额与未知份额分开，后者不会被清仓过滤隐藏。兼容字段使用nullish选择，0不会被旧别名覆盖。
5. 盈亏率仅格式化服务器浮盈/正剩余成本比例，证据不足或成本非正显示未知；不推算成本或价格。已实现盈亏始终显示“暂未提供”，不从交易记录模拟累计收益。
6. 交易记录维持当前访问范围该产品最近100条，未按来源账户拆分，明确说明边界。刷新先清除旧数据，列表失败不显示空持仓成功状态；部分价格失败有可重试提示。

## 验证证据

| 检查 | 结果 |
| --- | --- |
| 编辑前基线 | 依赖缺失时首次不能运行；`npm ci --ignore-scripts --prefix web`与shared build后采集原HEAD：PC Node 158/158；全量TS 63条历史诊断。原 Holdings 文件临时恢复采集后立即还原本任务版本 |
| 新增持仓回归 | `node --test web/pc-app/tests/holdingsExperience.test.mjs`：9项通过；无行情、日期/ETF/QDII/MMF/BOND_REPO、负值/0、旧DTO形状、筛选/路由、网络/401/403/单项NAV失败、旧请求/身份/卸载及详情标签切换 |
| 产品导航回归 | accountsProducts.test.mjs新增真实setup导航检查，能打开指定产品包括停用产品；不存在ID不误开其他产品 |
| 全套PC Node | `node --test web/pc-app/tests/*.test.mjs`：168/168通过 |
| 局部TS | `node web/node_modules/vue-tsc/bin/vue-tsc.js --noEmit -p web/pc-app/tests/tsconfig.holdings.json`：通过；覆盖Holdings、HoldingDetailModal、InitialHoldingImportModal、Products与新增模型 |
| 全量TS基线比对 | `npm --prefix web/pc-app run type-check`仍失败；63→62条，去除文件行号比较诊断多重集合，无新增，移除导入modal过滤query隐式any一条。其他历史错误未越界修复 |
| 浏览器 | Playwright CLI + Edge隔离fixture通过：搜索、清仓开关、负收益/未知金额、键盘详情/Esc、交易记录空态、产品往返/深链接、失败NAV与失败列表清旧值、人工重试、暗色及1440/1100/760宽度。业务HTTP全拦截，非本机域名中止，仅GET；pageerror 0。预设404/500产生预期console网络错误；产品页既有ElOption undefined告警仍存在 |
| 构建 | shared build与`powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0`通过；hook执行后端`mvn -DskipTests package`与shared/PC/H5 build，未运行Java单元测试。成功仅stdout/log，未更改hook行为 |
| Git | `git diff --check`通过；本地source commit，未push/部署 |

基线、最终测试/类型/浏览器/hook日志均在`web/pc-app/tests/holdings-*.log`（ignored）；截图在`web/pc-app/output/playwright/holdings-*.png`（ignored）。可复现浏览器：在PC目录启动Vite，绑定127.0.0.1:3110，用`npx --package @playwright/cli playwright-cli -s=holdings open about:blank --browser msedge`新建会话，再执行`run-code --filename tests/holdingsExperience.browser.js`。fixture仅用于隔离环境，不在真实登录或生产页面运行。

## 交付清单与限制

- Holdings.vue、holdingsViewModel.ts：筛选、兼容DTO、证据质量展示、生命周期门禁。
- HoldingDetailModal.vue、InitialHoldingImportModal.vue：原详情链保留、异步读保护及局部类型修正。
- Products.vue：从持仓定位产品只读档案。
- holdingsExperience.test.mjs、holdingsExperience.browser.js、accountsProducts.test.mjs、tsconfig.holdings.json：回归与类型门禁。
- `web/pc-app/delivery/task-aiw-r2-11-holdings-experience-20261010.json`：本地Delivery Manifest，源提交用包含manifest的Git提交解析。
- 本文：验收口径、证据及限制。

恢复旧页可对本任务最终提交执行git revert（仅说明，未执行）。真实owner/family后端授权隔离、生产契约、真实行情与人工导入/下单/结算端到端：**NOT_RUN**；mock响应不能证明后端角色授权。未部署，不宣称第二轮Goal整体完成。

外部AiCore Delivery Manifest登记与Hermes/企业微信回执：**PENDING_EXTERNAL_CHAIN / NOT_VERIFIED**。本次没有可调用AiCore/Hermes交付工具，外部路径也不在allowed_paths；未越界写调度或inbox。仅提供本地可解析manifest，goal_only外部汇总由AiCore/Hermes接续。
