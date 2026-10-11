# 财富中枢 PC：账户与产品管理交付

- 任务：`task-aiw-r2-10-accounts-products-20261010`；核对日期：2026-10-11（Asia/Hong_Kong）。
- 执行前 `git fetch origin v2`：HEAD 与 origin/v2 均为 `83a3fb062edbdd01b96cc82a26ab2f63145bde11`，工作树干净，包含首页重构依赖。独立本地 source commit，未 push、未部署、未创建后续 Task/Goal。
- 依据：AGENTS.md、AGENT.MD、DOCUMENT_INDEX、CURRENT_DEVELOPMENT_STATE 与 `wealthhub_pc_young_style_contract_20261010.md`。ZIP 本次未读取，使用已落地的纸感背景、23px 卡片、中文衬线标题、浅/暗/旧外观和既有设计变量。

## 实现与保留链

| 页面 | 交付 | 保留行为与证据 |
| --- | --- | --- |
| Accounts | 单一完整树替代重复的左右半树；名称/代码/类型搜索；父子、账户类型、真实/虚拟/持仓说明；键盘展开；只读详情；空态、错误、权限与人工重试 | 搜索保留匹配平台的完整子树，树与余额计算继续来自 shared；新增平台/信封、MMF编辑、改名、账户编辑/启停、余额调整与券商费率配置仍使用原处理函数及既有 API |
| Accounts 金额 | 负值、合法0按原值展示；缺值、缺行情显示未知；净值日期、报价时间与读取时间分开 | 不手写余额、不触发历史余额强刷；原 MMF/父余额计算不变，仅在证据不足时隐藏其推算值；缺行情不再以默认1净值/零价格展示资产 |
| Products | 场内/场外分栏、即时搜索、类别/启用筛选、停用标记、只读配置详情、错误/空态/重试、窄屏单列 | 产品新建/编辑、QDII兼容、费率百分比、分段天数、币种、确认延迟、截单时间、来源与备注保留；产品名继续跳转 Holdings 并带 productId |
| Products 排序 | 保留拖拽，增加键盘可用上移/下移 | 复用 shared `updateProductSortOrder`；键盘路径等待持久化，失败保留原顺序，不以本地顺序谎报成功 |
| Products 表单 | 分段费率读取失败给出引导并阻止未知配置覆盖；关闭和旧响应不能重新打开表单 | 原保存/校验/后端提交链保留；API未支持的删除、自动交易等按钮未引入；配置费率不宣称实际手续费 |
| 读取安全 | 最新请求与 token/userId/familyId 绑定；身份变化、停用/离开页面清除页面数据和详情表单；延迟初始化身份会重新读取 | 不增加家庭/成员作用域参数；产品是全局字典，不把家庭ID解释为管理员身份；401/403由shared原拦截器处理，并有页面错误状态；未修改shared认证或DTO |

页面列表读取直接使用既有 shared `accountApi.getAccounts` / `productApi.getProducts`，避免产品 store 吞错而将失败呈现为真实空列表。产品字段仍按现有 isQdii/isqdii、isActive 兼容规则处理；账户仍用 shared `buildAccountTree`。没有新后端契约，没有财务写入的自动触发。页面“重新读取”只读取列表/持仓/行情，不调用全局“重算历史余额”动作。

## 验证记录

| 验证 | 结果与范围 |
| --- | --- |
| 编辑前基线 | 首次因依赖未安装，Node缺typescript、vue-tsc不可执行；`npm ci --ignore-scripts --prefix web`及shared构建后重新采集：148/148 PC Node通过；全量vue-tsc历史71条诊断，其中Accounts 6、Products 2 |
| 针对性回归 | `node --test web/pc-app/tests/accountsProducts.test.mjs`：10项；树完整性、负值/合法0/未知、空与失败/401/403、旧请求/user/family/token、离开页面、初始化身份、原过滤/路由、键盘排序/失败回退、取消停用与后端验证失败、旧费率请求、表单关闭和保存链 |
| 全套 PC Node | `node --test web/pc-app/tests/*.test.mjs`：158/158通过 |
| 局部类型 | `node web/node_modules/vue-tsc/bin/vue-tsc.js --noEmit -p web/pc-app/tests/tsconfig.management.json`：通过；Accounts、Products及新增模型零诊断 |
| 全量类型 | `npm --prefix web/pc-app run type-check`：仍失败，历史71→63；新增/修改文件零诊断。修复产品私有路径类型导入，并移除当前页面未用符号；剩余为其他页面既有诊断，未扩大范围修改 |
| 隔离浏览器 | Playwright CLI + Edge，`tests/accountsProducts.browser.js`：通过；1440/1100/760宽度、父子搜索、负余额、键盘展开/排序、详情/Esc、暗色与切换零业务请求；页面JS错误0。全部业务HTTP拦截为fixture，非本机域名中止；排序POST仅mock |
| PC build / 编译 hook | PC Vite build及`powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0`通过；hook同时执行后端`mvn -DskipTests package`、shared/PC/H5构建，后端单元测试未运行；成功仅stdout/log，无Beep/Toast |
| 提交检查 | `git diff --check`通过；本地提交仅本任务allowed_paths |

基线/最终日志在 `web/pc-app/tests/accounts-products-*.log`（ignored）；截图在 `web/pc-app/output/playwright/`（ignored）。可复现浏览器检查：在web/pc-app启动Vite，绑定127.0.0.1:3110；从同目录用CLI新建about:blank Edge会话，再执行`run-code --filename tests/accountsProducts.browser.js`。不要在真实页面、真实登录或生产运行该fixture检查。

## 文件清单与验收边界

- `web/pc-app/src/views/Accounts.vue`、`Products.vue`：页面与读取状态。
- `web/pc-app/src/views/managementViewModel.ts`：只读筛选、数值判定、错误文案与请求门禁。
- `web/pc-app/src/styles/management-pages.css`：页面排版、主题与响应式样式。
- `web/pc-app/tests/accountsProducts.test.mjs`、`accountsProducts.browser.js`、`tsconfig.management.json`：隔离回归、浏览器流程、局部类型门禁。
- `web/pc-app/.gitignore`：沿用原规则，仅补充本地截图目录。
- 本文：任务交付清单。

真实跨用户/家庭角色后端隔离、真实设备/屏幕阅读器、生产契约和财务链端到端：**NOT_RUN**。Mock身份切换/403只能证明前端不会继续显示旧证据，不能证明后端授权正确。原账户scope/ownerType差异保留，不添加家庭汇总；真实角色验收另行安排。本地构建不代表部署或整个第二轮Goal验收。未访问数据库、真实账户/财务记录、券商、服务器；未执行交易、迁移、入账、结算、转账或通知。最终goal_only汇总由AiCore/Hermes负责。
