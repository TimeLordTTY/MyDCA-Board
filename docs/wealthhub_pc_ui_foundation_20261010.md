# 财富中枢 PC 年轻版 UI 基础交付

- 任务：`task-aiw-r2-07-design-tokens-components-20261010`；执行日期：2026-10-11 +08:00。
- 执行前 `git fetch origin v2`：HEAD / origin/v2 均为 `c7dcdfb236a8cc42a33f201ed705c704c51a17ae`，初始工作树干净（隔离 detached worktree）。R2-06 的[设计契约](wealthhub_pc_young_style_contract_20261010.md)已在该提交中，依赖未跳过。
- 修改范围仅 `web/pc-app/**` 与本文；不改 shared/API/Pinia/后端/SQL/Android/runtime/inbox，不连接数据库、不读写真实财务记录，不部署、不推送、不创建后续任务。依赖安装及既有编译命令只生成忽略的构建产物，没有修改范围外跟踪文件。

## 实现与交付清单

| 文件 | 职责 |
| --- | --- |
| `web/pc-app/src/styles/foundation.css` | 奶油白/墨色/薄荷、辅助蓝/桃、暗色变量，Element Plus 与原类桥接，基础 CSS 工具、键盘聚焦、禁用态、响应式、reduce-motion |
| `web/pc-app/src/styles/appearance.ts` | 版本化外观白名单、系统主题监听、即时应用及存储拒绝容错 |
| `web/pc-app/src/main.ts` | 在挂载前初始化外观，加载 Element Plus 暗色变量与基础样式 |
| `web/pc-app/src/components/AppearanceSettings.vue` | 有 label/for、键盘原生 select 的外观选择；支持浅色/深色/系统/旧版和三配色 |
| `web/pc-app/src/views/Settings.vue` | 只插入外观区，不改资料、安全、家庭逻辑 |
| `web/pc-app/src/components/FoundationState.vue` | loading/error/empty 文本及 DOM 语义，错误可显式启用人工重试，默认不发出操作 |
| `web/pc-app/tests/foundation.test.mjs` | 5 项 Node 回归：实际 Vue 状态组件 DOM、主题控制、白名单/损坏/拒绝存储、对比度、CSS 桥接与聚焦/禁用/断点 |
| `web/pc-app/tests/tsconfig.foundation.json` | 新基础层局部严格类型检查 |
| `web/pc-app/tests/foundation-browser.html`、`foundation.browser.js` | 不依赖认证/shared 的隔离浏览器验收页与 Playwright CLI 检查脚本；不进入生产入口、不提供业务模拟模式 |
| `web/pc-app/.gitignore` | 排除 Playwright CLI 自动生成的快照与浏览器记录，避免将本地验收产物提交 |

沿用 R2-06 的页面/卡片/墨色、23px 卡片圆角、999px 胶囊与 8/12/16/24/32px 间距。重要辅助文字使用更深的 `#62645b`（原 `#85867d` 仅可作边界/装饰），保证小字对比度。字体使用 Windows 本机 Microsoft YaHei，不请求外网字体。

主按钮蓝与装饰蓝/桃独立。涨跌仍为红涨绿跌，主题只调整同色系亮度以改善对比度；成功绿与上涨红是独立变量。没有金额格式化、增长动画、数据质量映射或业务状态变更。新增 CSS 工具如下：

| 工具 | 使用约定 |
| --- | --- |
| `.wh-card` | 纸感容器；调用方提供语义标题 |
| `.wh-number` | 等宽数字、响应字号；调用方保留币种/精度/真实0/未知语义 |
| `.wh-button`、`.wh-button--primary` | 用在原生 button；禁用必须使用原生 disabled |
| `.wh-chip` | 纯文字装饰标签；不推断 READY、收益或权限 |
| `.wh-label`、`.wh-input`、`.wh-form-row` | label 的 for 对应控件 id；44px 控件高度；窄屏纵向排列 |
| `.wh-note` | 来源或说明文字 |
| `FoundationState` | kind 必传；错误 role=alert，其他 role=status；加载 aria-busy；retryable 与 disabled 由调用方控制；组件不自行请求 |

## 外观与渐进过渡边界

默认浅色奶油；配色与明暗独立。`html[data-wh-theme]` / `data-wh-palette` 为主题入口；`.dark` 仅桥接 Element Plus 的既有暗色样式，teleport 弹窗也继承根变量。

设置页可恢复“旧版外观”，移除暗色 class，停止新旧样式变量桥接；业务路由与组件实例不重建。新基础组件仍可使用其专属样式。系统变化只更新主题属性；主题切换不触发业务事件/请求，浏览器验收确认正在编辑的输入保留。

唯一新增 localStorage key 为 `wealthhub.appearance.v1`，值只含 `{version:1,mode,palette}`。读取和写入均经白名单构造新对象，额外字段不复制；存储损坏回浅色奶油，浏览器拒绝访问或写入时外观仍可在当前会话应用。没有新增用户/账户/持仓/Token 持久化，也不改原认证存储机制。

原业务页面 DOM、Element Plus API 和 Pinia 状态保持原样，不复制 ZIP 的 DOM/JS。新基础样式覆盖原共享卡片/按钮/字段等；局部 scoped 业务样式继续渐进过渡，暗色下研究/雷达等保留可读的浅色纸面。**本交付不是全站业务页面暗色重绘**，不声明每一处历史内联颜色、图表、对话框已完成视觉/无障碍人工验收。既有业务测试通过只能证明相应测试覆盖内的行为；真实角色与完整键盘流程未执行。

## 验证与门禁

初始工作树没有 node_modules/shared dist：Node 测试出现 typescript/shared 模块缺失，type-check 缺 vue-tsc，属环境准备失败。执行 `npm ci --ignore-scripts --no-audit --no-fund` 与既有 `npm run build:shared` 后提取有效基线；类型基线临时恢复 HEAD 中 main.ts/Settings.vue 后检查，再恢复工作内容，不改业务历史错误。

| 检查 | 结果与边界 |
| --- | --- |
| 变更前 PC Node | `node --test web/pc-app/tests/*.test.mjs`：130/130 通过 |
| 变更前全量类型 | `node web/node_modules/vue-tsc/bin/vue-tsc.js --noEmit -p web/pc-app/tsconfig.json`：99 条 / 12 文件，失败 |
| 最终 PC Node | 同上 Node 命令：135/135 通过，0失败，含新增5项 |
| 最终全量类型 | 同上类型命令：99 条 / 12 文件；按包含文件/行号/错误码/内容的诊断逐条 Compare-Object，与基线完全一致，新增0条；仍失败 |
| 局部类型 | 逐个执行 `vue-tsc --noEmit -p web/pc-app/tests/tsconfig.*.json`：11/11 通过；新增 foundation 配置及10份既有配置全部通过 |
| PC build / 项目 hook | `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0`：通过；后端只执行 `mvn -DskipTests package`，前端执行原 shared/PC/mobile build；未运行数据库集成测试，无自动修复越界 |
| 浏览器 | Playwright CLI + 本地 Edge headless，1440/1080/390px × light/dark × cream/blue/peach：18/18；无横向溢出，输入未被主题重置，禁用保持；额外验证系统暗色、reduce-motion、键盘焦点、旧版重载恢复、持久化字段白名单 |
| 静态对比度 | 普通文字/辅助文字/成功/危险/警告/涨跌文字及主按钮 ≥4.5:1；焦点/控件边界 ≥3:1；三种装饰面文字 ≥4.5:1；不代表所有历史页颜色已验收 |
| 浏览器计算对比度 | Element Plus 主按钮（含 hover）浅色5.87:1、深色8.75:1；卡片正文浅色15.32:1、深色14.02:1 |
| 差异检查 | `git diff --check` 通过；仅允许路径内跟踪文件修改 |

浏览器首次默认 Chrome 缺失，改用已安装 Edge；首次测量抓到 Element Plus 尚未结束的颜色过渡，验收脚本等待颜色稳定后测量，并覆盖 hover。隔离页 favicon 404，不是业务请求或组件异常。验证过程中没有登录、加载真实账号或调用业务 API；脚本拦截非 127.0.0.1 和 /api/ 请求。

复现浏览器：在 `web/pc-app` 执行 `npm run dev -- --host 127.0.0.1 --port 3107`；另一个终端运行：

```powershell
npx --yes --package @playwright/cli playwright-cli -s=foundation open http://127.0.0.1:3107/wealth-hub/tests/foundation-browser.html --browser msedge
npx --yes --package @playwright/cli playwright-cli -s=foundation run-code --filename tests/foundation.browser.js
npx --yes --package @playwright/cli playwright-cli -s=foundation close
```

全量类型既有失败分布：InitialHoldingImportModal 1、NewOrderModal 1、QuickEntryModal 2、RefundModal 2、ReimburseModal 2、UnifiedEntryModal 42、MainLayout 2、router 1、Accounts 6、Dashboard 27、Orders 11、Products 2，共99。逐条证据见下文，基线与最终一致。

**交付状态：实现、针对性测试及编译通过；全量 vue-tsc 门禁因既有99条诊断仍不通过。不得解释为全部类型/上线验收通过，不代替调度器解除失败门禁，不跳过后续任务依赖。** 本任务只本地 source commit，完整 Goal 交付通知仍由 AiCore/Hermes 汇总；没有自动发布。

## 全量类型失败证据（基线与最终相同）

```text
web/pc-app/src/components/InitialHoldingImportModal.vue(47,32): error TS7006: Parameter 'query' implicitly has an 'any' type.
web/pc-app/src/components/NewOrderModal.vue(218,38): error TS6133: 'acc' is declared but its value is never read.
web/pc-app/src/components/QuickEntryModal.vue(58,96): error TS6133: 'getCategoryDisplayName' is declared but its value is never read.
web/pc-app/src/components/QuickEntryModal.vue(59,24): error TS6196: 'Category' is declared but never used.
web/pc-app/src/components/RefundModal.vue(131,11): error TS6133: 'txnAny' is declared but its value is never read.
web/pc-app/src/components/RefundModal.vue(134,39): error TS2339: Property 'occurredAt' does not exist on type 'LedgerTxn'.
web/pc-app/src/components/ReimburseModal.vue(96,9): error TS6133: 'reimburseCategory' is declared but its value is never read.
web/pc-app/src/components/ReimburseModal.vue(189,11): error TS6133: 'categoryId' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(77,39): error TS6133: 'node' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(132,43): error TS6133: 'node' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(194,39): error TS6133: 'node' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(267,41): error TS6133: 'node' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(289,41): error TS6133: 'node' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(473,39): error TS6133: 'node' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(585,39): error TS6133: 'node' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(771,39): error TS6133: 'node' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(913,39): error TS6133: 'node' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(1056,39): error TS6133: 'node' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(1151,39): error TS6133: 'node' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(1177,39): error TS6133: 'node' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(1214,33): error TS6133: 'getFundUsageLabel' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(1337,7): error TS6133: 'parentAccounts' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(1376,7): error TS6133: 'availableChildAccounts' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(1392,7): error TS6133: 'availableFromChildAccounts' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(1408,7): error TS6133: 'availableToChildAccounts' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(1683,7): error TS6133: 'expenseAllocationError' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(1813,7): error TS6133: 'selectedParentAccountBalance' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(1814,7): error TS6133: 'selectedParentAccountCredit' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(1817,7): error TS6133: 'selectedChildAccountBalance' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(1825,7): error TS6133: 'isSelectedChildCreditAccount' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(1839,7): error TS6133: 'selectedFromParentAccountBalance' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(1840,7): error TS6133: 'selectedFromParentAccountCredit' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(1843,7): error TS6133: 'selectedFromChildAccountBalance' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(1851,7): error TS6133: 'isSelectedFromChildCreditAccount' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(1865,7): error TS6133: 'selectedToParentAccountBalance' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(1866,7): error TS6133: 'selectedToParentAccountCredit' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(1869,7): error TS6133: 'selectedToChildAccountBalance' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(1877,7): error TS6133: 'isSelectedToChildCreditAccount' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(2247,36): error TS2322: Type 'string' is not assignable to type 'number'.
web/pc-app/src/components/UnifiedEntryModal.vue(2257,38): error TS2322: Type 'string' is not assignable to type 'number'.
web/pc-app/src/components/UnifiedEntryModal.vue(2564,10): error TS6133: 'handleParentAccountChange' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(2568,10): error TS6133: 'handleRepaymentAccountChange' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(2703,10): error TS6133: 'handleFromParentAccountChange' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(2708,10): error TS6133: 'handleToParentAccountChange' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(2713,10): error TS6133: 'handleBuyParentAccountChange' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(2719,10): error TS6133: 'handleBuyAccountChange' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(2734,10): error TS6133: 'handleSellParentAccountChange' is declared but its value is never read.
web/pc-app/src/components/UnifiedEntryModal.vue(3885,13): error TS2353: Object literal may only specify known properties, and 'requestedAt' does not exist in type 'CreateOrderRequest'.
web/pc-app/src/components/UnifiedEntryModal.vue(4065,18): error TS2367: This comparison appears to be unintentional because the types '"OTC"' and '"EXCHANGE"' have no overlap.
web/pc-app/src/components/UnifiedEntryModal.vue(4103,36): error TS2345: Argument of type '{ orderType: "BUY" | "SELL" | "SUBSCRIPTION" | "REDEMPTION"; productId: number; shares: number; amount: number; requestedAt: string; expectedNavDate: string | undefined; expectedConfirmDate: string | undefined; feeEstimate: number | undefined; note: string; fundingLines: { ...; }[]; }' is not assignable to parameter of type 'CreateOrderRequest'.
web/pc-app/src/layouts/MainLayout.vue(180,10): error TS6133: 'handleQuickExpense' is declared but its value is never read.
web/pc-app/src/layouts/MainLayout.vue(184,10): error TS6133: 'handleQuickIncome' is declared but its value is never read.
web/pc-app/src/router/index.ts(95,24): error TS6133: 'from' is declared but its value is never read.
web/pc-app/src/views/Accounts.vue(829,3): error TS6133: 'accountApi' is declared but its value is never read.
web/pc-app/src/views/Accounts.vue(835,58): error TS6196: 'Nav' is declared but never used.
web/pc-app/src/views/Accounts.vue(1044,10): error TS6133: 'getNonAllocatedChildrenCount' is declared but its value is never read.
web/pc-app/src/views/Accounts.vue(1094,10): error TS6133: 'getMmfChildAmount' is declared but its value is never read.
web/pc-app/src/views/Accounts.vue(1094,28): error TS6133: 'platform' is declared but its value is never read.
web/pc-app/src/views/Accounts.vue(1505,62): error TS6133: 'index' is declared but its value is never read.
web/pc-app/src/views/Dashboard.vue(52,41): error TS2339: Property 'amount' does not exist on type '{ confirmDate: string; navDate: string; confirmNav: number | undefined; confirmShares: number | undefined; confirmAmount: number | undefined; confirmFee: number; note: string; }'.
web/pc-app/src/views/Dashboard.vue(68,41): error TS2339: Property 'shares' does not exist on type '{ confirmDate: string; navDate: string; confirmNav: number | undefined; confirmShares: number | undefined; confirmAmount: number | undefined; confirmFee: number; note: string; }'.
web/pc-app/src/views/Dashboard.vue(467,45): error TS2339: Property 'handleConfirmSettlement' does not exist on type '{ $: ComponentInternalInstance; $data: {}; $props: Partial<{}> & Omit<{} & VNodeProps & AllowedComponentProps & ComponentCustomProps, never>; ... 11 more ...; $watch<T extends string | ((...args: any) => any)>(source: T, cb: T extends (...args: any) => infer R ? (args_0: R, args_1: R, args_2: OnCleanup) => any : (ar...'.
web/pc-app/src/views/Dashboard.vue(748,28): error TS2339: Property 'amount' does not exist on type '{ confirmDate: string; navDate: string; confirmNav: number | undefined; confirmShares: number | undefined; confirmAmount: number | undefined; confirmFee: number; note: string; }'.
web/pc-app/src/views/Dashboard.vue(750,28): error TS2339: Property 'amount' does not exist on type '{ confirmDate: string; navDate: string; confirmNav: number | undefined; confirmShares: number | undefined; confirmAmount: number | undefined; confirmFee: number; note: string; }'.
web/pc-app/src/views/Dashboard.vue(752,26): error TS2339: Property 'shares' does not exist on type '{ confirmDate: string; navDate: string; confirmNav: number | undefined; confirmShares: number | undefined; confirmAmount: number | undefined; confirmFee: number; note: string; }'.
web/pc-app/src/views/Dashboard.vue(809,41): error TS2339: Property 'amount' does not exist on type '{ confirmDate: string; navDate: string; confirmNav: number | undefined; confirmShares: number | undefined; confirmAmount: number | undefined; confirmFee: number; note: string; }'.
web/pc-app/src/views/Dashboard.vue(812,48): error TS2339: Property 'amount' does not exist on type '{ confirmDate: string; navDate: string; confirmNav: number | undefined; confirmShares: number | undefined; confirmAmount: number | undefined; confirmFee: number; note: string; }'.
web/pc-app/src/views/Dashboard.vue(814,49): error TS2339: Property 'shares' does not exist on type '{ confirmDate: string; navDate: string; confirmNav: number | undefined; confirmShares: number | undefined; confirmAmount: number | undefined; confirmFee: number; note: string; }'.
web/pc-app/src/views/Dashboard.vue(816,75): error TS2339: Property 'shares' does not exist on type '{ confirmDate: string; navDate: string; confirmNav: number | undefined; confirmShares: number | undefined; confirmAmount: number | undefined; confirmFee: number; note: string; }'.
web/pc-app/src/views/Dashboard.vue(829,28): error TS2339: Property 'amount' does not exist on type '{ confirmDate: string; navDate: string; confirmNav: number | undefined; confirmShares: number | undefined; confirmAmount: number | undefined; confirmFee: number; note: string; }'.
web/pc-app/src/views/Dashboard.vue(832,44): error TS2339: Property 'amount' does not exist on type '{ confirmDate: string; navDate: string; confirmNav: number | undefined; confirmShares: number | undefined; confirmAmount: number | undefined; confirmFee: number; note: string; }'.
web/pc-app/src/views/Dashboard.vue(840,28): error TS2339: Property 'shares' does not exist on type '{ confirmDate: string; navDate: string; confirmNav: number | undefined; confirmShares: number | undefined; confirmAmount: number | undefined; confirmFee: number; note: string; }'.
web/pc-app/src/views/Dashboard.vue(841,71): error TS2339: Property 'shares' does not exist on type '{ confirmDate: string; navDate: string; confirmNav: number | undefined; confirmShares: number | undefined; confirmAmount: number | undefined; confirmFee: number; note: string; }'.
web/pc-app/src/views/Dashboard.vue(851,39): error TS2339: Property 'amount' does not exist on type '{ confirmDate: string; navDate: string; confirmNav: number | undefined; confirmShares: number | undefined; confirmAmount: number | undefined; confirmFee: number; note: string; }'.
web/pc-app/src/views/Dashboard.vue(854,46): error TS2339: Property 'amount' does not exist on type '{ confirmDate: string; navDate: string; confirmNav: number | undefined; confirmShares: number | undefined; confirmAmount: number | undefined; confirmFee: number; note: string; }'.
web/pc-app/src/views/Dashboard.vue(856,47): error TS2339: Property 'shares' does not exist on type '{ confirmDate: string; navDate: string; confirmNav: number | undefined; confirmShares: number | undefined; confirmAmount: number | undefined; confirmFee: number; note: string; }'.
web/pc-app/src/views/Dashboard.vue(858,73): error TS2339: Property 'shares' does not exist on type '{ confirmDate: string; navDate: string; confirmNav: number | undefined; confirmShares: number | undefined; confirmAmount: number | undefined; confirmFee: number; note: string; }'.
web/pc-app/src/views/Dashboard.vue(888,10): error TS6133: 'calculateSettlementFromForm' is declared but its value is never read.
web/pc-app/src/views/Dashboard.vue(941,31): error TS2339: Property 'amount' does not exist on type '{ confirmDate: string; navDate: string; confirmNav: number | undefined; confirmShares: number | undefined; confirmAmount: number | undefined; confirmFee: number; note: string; }'.
web/pc-app/src/views/Dashboard.vue(941,62): error TS2339: Property 'amount' does not exist on type '{ confirmDate: string; navDate: string; confirmNav: number | undefined; confirmShares: number | undefined; confirmAmount: number | undefined; confirmFee: number; note: string; }'.
web/pc-app/src/views/Dashboard.vue(950,31): error TS2339: Property 'shares' does not exist on type '{ confirmDate: string; navDate: string; confirmNav: number | undefined; confirmShares: number | undefined; confirmAmount: number | undefined; confirmFee: number; note: string; }'.
web/pc-app/src/views/Dashboard.vue(950,62): error TS2339: Property 'shares' does not exist on type '{ confirmDate: string; navDate: string; confirmNav: number | undefined; confirmShares: number | undefined; confirmAmount: number | undefined; confirmFee: number; note: string; }'.
web/pc-app/src/views/Dashboard.vue(1140,28): error TS2339: Property 'amount' does not exist on type '{ confirmDate: string; navDate: string; confirmNav: number | undefined; confirmShares: number | undefined; confirmAmount: number | undefined; confirmFee: number; note: string; }'.
web/pc-app/src/views/Dashboard.vue(1141,55): error TS2339: Property 'amount' does not exist on type '{ confirmDate: string; navDate: string; confirmNav: number | undefined; confirmShares: number | undefined; confirmAmount: number | undefined; confirmFee: number; note: string; }'.
web/pc-app/src/views/Dashboard.vue(1203,28): error TS2339: Property 'shares' does not exist on type '{ confirmDate: string; navDate: string; confirmNav: number | undefined; confirmShares: number | undefined; confirmAmount: number | undefined; confirmFee: number; note: string; }'.
web/pc-app/src/views/Dashboard.vue(1204,33): error TS2339: Property 'shares' does not exist on type '{ confirmDate: string; navDate: string; confirmNav: number | undefined; confirmShares: number | undefined; confirmAmount: number | undefined; confirmFee: number; note: string; }'.
web/pc-app/src/views/Orders.vue(297,36): error TS6133: 'watch' is declared but its value is never read.
web/pc-app/src/views/Orders.vue(427,38): error TS2339: Property 'navData' does not exist on type '{ fundingLines: { id: number; orderId: string; lineNo: number; accountId: number; amount: number; shares?: number | undefined; currency: "CNY" | "USD" | "HKD"; lineType?: "SOURCE" | "TARGET" | undefined; createdAt: string; updatedAt: string; }[]; ... 16 more ...; updatedAt: string; }'.
web/pc-app/src/views/Orders.vue(428,35): error TS2339: Property 'navData' does not exist on type '{ fundingLines: { id: number; orderId: string; lineNo: number; accountId: number; amount: number; shares?: number | undefined; currency: "CNY" | "USD" | "HKD"; lineType?: "SOURCE" | "TARGET" | undefined; createdAt: string; updatedAt: string; }[]; ... 16 more ...; updatedAt: string; }'.
web/pc-app/src/views/Orders.vue(437,40): error TS2339: Property 'navData' does not exist on type '{ fundingLines: { id: number; orderId: string; lineNo: number; accountId: number; amount: number; shares?: number | undefined; currency: "CNY" | "USD" | "HKD"; lineType?: "SOURCE" | "TARGET" | undefined; createdAt: string; updatedAt: string; }[]; ... 16 more ...; updatedAt: string; }'.
web/pc-app/src/views/Orders.vue(438,37): error TS2339: Property 'navData' does not exist on type '{ fundingLines: { id: number; orderId: string; lineNo: number; accountId: number; amount: number; shares?: number | undefined; currency: "CNY" | "USD" | "HKD"; lineType?: "SOURCE" | "TARGET" | undefined; createdAt: string; updatedAt: string; }[]; ... 16 more ...; updatedAt: string; }'.
web/pc-app/src/views/Orders.vue(451,38): error TS2339: Property 'navData' does not exist on type '{ fundingLines: { id: number; orderId: string; lineNo: number; accountId: number; amount: number; shares?: number | undefined; currency: "CNY" | "USD" | "HKD"; lineType?: "SOURCE" | "TARGET" | undefined; createdAt: string; updatedAt: string; }[]; ... 16 more ...; updatedAt: string; }'.
web/pc-app/src/views/Orders.vue(452,35): error TS2339: Property 'navData' does not exist on type '{ fundingLines: { id: number; orderId: string; lineNo: number; accountId: number; amount: number; shares?: number | undefined; currency: "CNY" | "USD" | "HKD"; lineType?: "SOURCE" | "TARGET" | undefined; createdAt: string; updatedAt: string; }[]; ... 16 more ...; updatedAt: string; }'.
web/pc-app/src/views/Orders.vue(747,10): error TS6133: 'getOrderConfirmDate' is declared but its value is never read.
web/pc-app/src/views/Orders.vue(860,10): error TS6133: 'getOrderFee' is declared but its value is never read.
web/pc-app/src/views/Orders.vue(1111,10): error TS6133: 'calculateShares' is declared but its value is never read.
web/pc-app/src/views/Orders.vue(1117,10): error TS6133: 'calculateAmount' is declared but its value is never read.
web/pc-app/src/views/Products.vue(465,38): error TS2307: Cannot find module '@wealth-hub/shared/src/api/product' or its corresponding type declarations.
web/pc-app/src/views/Products.vue(529,7): error TS6133: 'sellFeeRatePercent' is declared but its value is never read.
```
