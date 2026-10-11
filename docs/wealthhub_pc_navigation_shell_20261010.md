# 财富中枢 PC 导航壳交付与验收

- 任务：`task-aiw-r2-08-navigation-shell-20261010`；日期：2026-10-11（Asia/Hong_Kong）。
- 执行前 `git fetch origin v2`；工作树干净，HEAD 与 origin/v2 均为 `12636c1e16770847fc2c40868a1307ba49a7618e`，已包含前置设计令牌/组件任务。仅本地 source commit，不推送、不部署，不修改 inbox、审批、调度或后续任务。
- 仅修改 `web/pc-app/**` 与本文。未修改 shared、后端、SQL、Android；未连接数据库、生产服务，不执行真实财务操作。编译 hook 的后端步骤为 `mvn -DskipTests package`，不能解释成后端单元测试通过。

## 实现与完整入口

MainLayout 顶部拥挤导航迁移为默认 82px 图标轨，显式按钮固定展开至 222px；hover / focus-within 临时展开覆盖侧边区域，避免推移业务表单。每项有名称、原生标题提示和当前页标记。导航自身可滚动，末项设置及站点目录可达。768px 及以下使用 Element Plus drawer，保留焦点约束、Esc/遮罩/关闭按钮、选页关闭、路由变化关闭、宽屏关闭和关闭后焦点返回目录按钮。

| 页面 | 路径 | 页面 | 路径 |
| --- | --- | --- | --- |
| 总览 | `/dashboard` | 目标与预算 | `/goal-budget-center` |
| 流水 | `/ledger` | 草稿箱 | `/drafts` |
| 订单与结算 | `/orders` | 待结算与审计 | `/settlements` |
| 产品 | `/products` | 账户 | `/accounts` |
| 持仓管理 | `/holdings` | 策略实验室 | `/strategy-lab` |
| 风险观察 | `/risk-center` | 配置观察 | `/rebalance-center` |
| 数据就绪检查 | `/data-readiness` | 设置 | `/settings` |

14 个业务子路由全部覆盖，补足旧导航未列出的 Settlements；保留独立 Portal 站点目录入口及两个登录页。没有添加不受支持的外部模块或演示链接。router 与 Vite 配置未修改，沿用 `/wealth-hub/` history base、JWT 本地 token 守卫、redirect 查询和页面级权限校验。

主区域 header 显示业务标题、分组面包屑、真实账号和家庭关联。首次身份缺失时复用 shared `fetchCurrentUser()` GET；读取失败显示“身份未确认”，不猜测角色。UserInfo 无家庭角色/当前页面 scope 字段，因此明确注明“当前作用域以页面选择为准”，不冒充家庭管理员或全站家庭作用域。账号链接进入原设置页。安全退出调用 shared logout，网络失败也清理本地 token 和 Pinia 身份，以 replace 返回登录。

原有刷新行情、统一记账入口及弹窗保留；全局动作继续在目标预算和数据就绪页隐藏。“刷新数据（重算历史余额）”明确揭示原副作用，只由主动点击执行，执行期间禁用以防重入。挂载、路由、展开、主题切换均不触发重算或财务写入。原页面 DRAFT / preview / confirm 链、0 / UNKNOWN / PARTIAL 语义未改。主题复用前置令牌及外观偏好，包含浅色/深色/系统/旧版配色；恢复旧版整体布局可回退本任务 source commit，未引入两套业务实现。

header 位于正文滚动容器之外，不以 sticky 覆盖页面。正文独立滚动，导航切换复位滚动位置，提供跳到主内容链接。窄屏标题工具可换行；短屏 header 限高并可滚动，保留正文空间。业务表格仍沿用其原有横向滚动与布局，不扩展到本任务之外的页面重构。

## 验证与限制

| 检查 | 结果 |
| --- | --- |
| 依赖准备 | `npm ci --ignore-scripts --prefix web`，构建 shared 产物后提取有效基线；最初缺依赖失败不计为代码诊断 |
| 原版 PC Node | 135 / 135 通过 |
| 最终全套 PC Node | 140 / 140 通过，含新增导航测试及更新原有全局动作隔离断言 |
| 局部类型 | `node web/node_modules/vue-tsc/bin/vue-tsc.js --noEmit -p web/pc-app/tests/tsconfig.navigation.json` 通过 |
| 全量 vue-tsc | 原版 99 项、最终 97 项诊断，均退出 2；去除行号比较无新增，减少 MainLayout 两个未使用快速入口函数诊断；MainLayout / WealthNavigation / navigation.ts 无诊断 |
| PC build | Vite build 通过；既有大 chunk 提示不作为类型通过证据 |
| 全仓编译 hook | `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0` 通过，后端打包及 shared/PC/mobile 构建成功，hook 保持现有静默行为 |
| `git diff --check` | 通过 |
| 浏览器 | Playwright CLI + Edge 桌面及 mobile context；1440 / 1080 / 390 宽度，4 种主题模式通过 |

浏览器使用 `tests/navigation-browser.html`：真实 MainLayout、导航、router history/守卫及外观组件，业务页替换成隔离夹具；最后再加载真实持仓页检查 deep-link 刷新。全部网络请求拦截，仅允许本机静态资源；业务 API 使用离线 mock，退出 POST 模拟断网，其余全部 GET。覆盖 14 个链接/标题/active、目标预算/就绪动作隔离、hover/键盘/显式展开、主题保留输入、正文滚动和路由复位、抽屉20次Tab焦点约束/Esc/遮罩/选页/resize关闭、移动 tap、离线登出、无 token 深链接与 query redirect、真实页刷新。

复现：在 `web/pc-app` 运行 `npm run dev -- --host 127.0.0.1 --port 3108`；另用 `npx --yes --package @playwright/cli playwright-cli -s=wealth-navigation open about:blank --browser msedge`，再执行 `run-code --filename tests/navigation.browser.js`（同一 session）。触屏 session 添加 `--mobile`。Node：`node --test web/pc-app/tests/*.test.mjs`。

初始缺依赖、首次夹具重复布局/选择到隐藏遮罩等失败均已纠正并重跑。有效基线及最终验证日志保留于本地 `web/pc-app/tests/navigation-*.log`（仓库忽略），交付测试代码可复现。未宣称全量类型检查清零、真实角色/设备人工验收、生产部署或整体 Goal 完成；正式通知由 AiCore/Hermes 汇总。
