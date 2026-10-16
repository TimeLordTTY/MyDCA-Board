# 无限探索门户入口与私有会话接续

日期：2026-10-16，Asia/Hong_Kong。任务 `task-ie-wealthhub-portal-session-20261015`。本结果是隔离仓库实现和全 mock 验证，未部署、未启用生产入口、未使用真实账号、未连接生产数据库、未修改真实财务记录、未交易、未推送。正式 Delivery 和通知由原 Runtime/Finalizer/Hermes 处理，本报告不替代其验收。

## 输入与范围

首先读取原 pending 任务、AGENTS.md，再读取 DOCUMENT_INDEX、AGENT.MD、CURRENT_DEVELOPMENT_STATE、认证调查（含 2026-10-16 生产源码身份补证）、后端专项报告及实际 Controller/Security/会话代码。已核验 AiCore `reports/delivery/task-ie-wealthhub-private-session-20261015.json` 的 completed、result_commit 和已验证通知回执：`835630451cae7d7e41acf25fcb2dd4ad6d08a9d2`，与本控制器工作树输入一致。没有同步/合并 v2、改动原 B 固定基线或正在使用的其他 checkout。

仅交付以下 7 个 allowlist 路径，唯一一个本地结果提交包含全部源、测试和本报告：

| 路径 | 作用 |
| --- | --- |
| `web/pc-app/src/views/Portal.vue` | 原目录内增加入口、可朗读状态、受保护的注销收尾；保留现有卡片布局 |
| `web/pc-app/src/auth/privateSites.ts` | 私有会话接续、Cookie CSRF 撤销、串行队列、失效事件 |
| `web/pc-app/src/auth/session.ts` | 身份恢复版本校验、退出/切账号钩子、窄范围私有 ACL 响应处理 |
| `web/pc-app/tests/privateSites.test.mjs` | 25 项源码绑定 Node 测试 |
| `web/pc-app/tests/privateSites.browser.js` | 最终隔离 Edge 全 mock fixture |
| `web/pc-app/tests/tsconfig.privateSites.json` | 私有会话/门户局部类型检查 |
| `docs/infinite_exploration_portal_entry_20261015.md` | 本报告 |

`backend`、`web/shared`、兼容 H5、锁文件相对输入的定向 Git diff 为空；没有修改 SQL、依赖、生产配置、调度、任务审批、Finalizer、Hermes、Android、诗词站、游戏墙或 IE 源码。应用 karpathy-guidelines、frontend-design（沿用原视觉）与 playwright 技能。

## 实现行为和协议对应

门户挂载时重新读取原 `/users/me`；点击入口时再验证当前身份，恢复失败不会开始 exchange。localStorage 有 JWT 只用于原 Bearer 客户端，不等于 Owner 授权。有效非 Owner 由实际后端 ACL 决定，不按用户名、角色或家庭管理员推断 Owner。已存在同源 PC/兼容 H5 Bearer 的浏览器可从本 PC 门户接续，不增加密码输入或用户库；没有把新逻辑安装到兼容 H5/Android 应用。

| 已交付接口 | 本次调用方式 | 成功/拒绝行为 |
| --- | --- | --- |
| POST `/api/v2/private-sites/session/exchange` | 原 apiClient 的 Bearer，自定义 `X-Private-Site-Request: 1`，请求 no-store | 仅 HTTP 200、authenticated=true、有效 CSRF/未来 expiresAt、响应 no-store 才跳转 |
| GET `/api/v2/private-sites/session` | 同源 fetch、Cookie、cache=no-store、redirect=error、自定义头，无 Authorization | 重新获取当前 Cookie 的 CSRF；401 表示无有效会话，无需再注销 |
| POST `/api/v2/private-sites/session/logout` | 同源 fetch、Cookie、自定义头、服务端返回的 `X-CSRF-Token`，无 Authorization | 204/401 且 no-store 才认定已撤销/已失效；错误保留“撤销未获确认”状态 |
| GET `/explore/` | 仅固定字符串整页导航 | 不接受服务端 redirect 或任意输入目的地；文件授权继续由已交付后端/Nginx 负责 |

Cookie state/logout 没有使用 shared Bearer 拦截器，因为上游明确拒绝此两类请求混入 Authorization；exchange 仍使用原 apiClient。未建立第二份服务端会话协议。新端点所有响应的 no-store 由现有 PrivateSiteSecurity 保证，本次对成功状态另做客户端校验；Cookie 接续请求不使用缓存。缺少发布配置时后端仍默认关闭，门户安全显示未启用/不可用，不提前读取私有文件。

exchange 401 显示身份/会话过期；403 显示当前账号无权限或来源校验未通过、仅限 Owner；404/503 显示未启用或暂不可用；429 显示容量上限；网络、非 JSON、缓存性响应和过期状态均不跳转，支持手动重试。私有 403 不会误清 Wealth Hub JWT；原金融 API 401/403 仍清理 JWT/身份/缓存并跳回原登录边界。错误文案不复制服务端响应中的任意秘密。

每次激活都重新验证并 exchange，不保存或复用成功标记。重复点击共用一个请求。私有 Cookie 操作在当前页面内串行，失效版本立即阻断导航，延迟旧 exchange 之后的撤销先于新账号 exchange。原身份读取还校验请求序号、JWT 和 sessionRevision；原响应边界新增 revision 校验，防止 token A→B→A 文本相同的旧响应复活。门户离开、原身份过期、切账号及退出都会失效接续。旧门户注销等待结束后再次检查账号/版本，不能清理新标签页已恢复的另一账号。

Portal 显式先撤销私有会话，再执行原 logout；session 拦截 `/auth/logout`，也覆盖 PC 主布局的原退出入口。原 JWT 本地清理保留，旧服务离线仍完成本地退出；网络无法确认私有撤销时不声称已撤销，仍须依赖重试、服务端到期和下游拒绝来收敛。没有修改原无状态 JWT 的服务端撤销语义。

没有将 JWT 放进 URL/query/hash/新增日志或事件；没有读取 HttpOnly Cookie，没有新增 Token/CSRF 持久化。原 localStorage token 机制保留。诗词跨 origin 的两个链接仍以 noopener noreferrer 独立打开；游戏墙文案明确由该站校验，不再总括宣称所有子站已共享 Wealth Hub 认证。

## 跨标签接口和下游缺口

本次实现的安全通知为同源、仅失效、无授权能力的事件：

| 通道 | 精确接口 |
| --- | --- |
| 同页 CustomEvent | `wealth-hub:private-session-invalidated`，detail 为 `{version:1, reason, nonce}` |
| 其他同源标签页 | localStorage key `wealth-hub:private-session-event` 的 storage 事件，newValue 为同样的 JSON 元数据 |
| 原 JWT 切换 | 既有 `token` 的 storage 事件及 key=null 的清空事件继续处理 |

reason 为 logout/account-change/identity-change/portal-leave 等非秘密原因，nonce 为随机事件 ID。接收方只能用它清空/停止，不得视为已登录或 Owner 凭证。门户接收事件立即废弃旧响应并尝试撤销私有 Cookie。没有复制凭据到诗词域或设置 Domain Cookie。

**下游即时清空为明确接口缺口，未宣称全站即时退出完成。** 已读取的认证调查与 IE 实施设计只证明 IE 在 401/403/非 JSON 认证响应时清图、停刷新；本任务输入树没有 IE UI 源码，也没有取得上述新增事件监听已经交付的证据。因此发布验收须将其视为未接通：IE 的 `app.js` 应监听上述 CustomEvent/storage 及原 token 删除/变更，立即取消在途 manifest/snapshot、清空双森林图和敏感详情、停止轮询，令所有旧响应失效，并要求显式重新认证/重载。还应在 pageshow（含 bfcache 恢复）重新校验，不能用 90 秒轮询代替即时失效。此处是接口交接要求，不是已实现的 IE 修改。

该事件是浏览器尽力通知，不是服务器授权。断网、关闭标签页、不同 origin、未接监听及多页面网络交错不能由本地事件保证立即服务端撤销；现有服务端 Cookie 期限/逐请求 Owner 校验仍是保护边界。本 fixture 只验证门户与合成私有协议，不将 stub 页面视为真实 IE 内容或线上认证成功。

## 控制器基线与实际验证

唯一比较来源：`D:/Project/ai-core-runtime/agent-runtime/dispatch/dual-worker-state/runs/20261016-021410-32364-0/controller-baseline/baseline.json` 和同目录 `types-baseline.log`。控制器 status=passed，base=`835630451cae7d7e41acf25fcb2dd4ad6d08a9d2`，dependencies/shared-build/types-baseline 均退出 0；type_error_count=0、diagnostics={}。types-baseline.log 原始空文件 SHA256 为 `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`，本次只读复核一致。

最终同命令全量类型退出 0、stdout 无诊断，故 **控制器基线 0 → 最终 0，新增诊断 0**。未修改任一树做类型对照，未用 git show/checkout/reset/restore 替换源码，未编辑控制器 worktree 或重跑/自建 baseline。源码绑定 Node 测试在内存转译实际 TS/SFC 并注入 mock 依赖，不覆盖当前源文件。

| 原生命令 | 最终实际结果 |
| --- | --- |
| `npm.cmd --prefix web ci --ignore-scripts --no-audit --no-fund` | 退出 0，156 包，原锁文件未改 |
| `npm.cmd --prefix web/shared run build` | 退出 0，初始共享构建准备 |
| `npm.cmd --prefix web run build` | 最终源码之后重跑退出 0；shared、PC、兼容 H5 全通过，PC 2195 modules、H5 386 modules；保留 npm 配置和 chunk 体积警告 |
| `node --test --test-concurrency=1 web/pc-app/tests/*.test.mjs` | 最终退出 0，298/298，无跳过，39.156 秒；含原 Portal/session 回归和新增 25 项 |
| `node --test --test-concurrency=1 web/pc-app/tests/privateSites.test.mjs web/pc-app/tests/portalAuth.test.mjs` | 最终退出 0，31/31 |
| `node web/node_modules/vue-tsc/bin/vue-tsc.js --noEmit -p web/pc-app/tests/tsconfig.privateSites.json` | 最终退出 0，无诊断 |
| `node web/node_modules/vue-tsc/bin/vue-tsc.js --noEmit -p web/pc-app/tsconfig.json` | 最终退出 0，无诊断，与控制器相同命令 |
| `mvn -f backend/pom.xml test` | 退出 0；374 项，0 failure/error，20 skipped；10:23:43 +08:00 完成，1:32 min |
| `mvn -f backend/pom.xml -DskipTests package` | 退出 0，BUILD SUCCESS；10:25:00 +08:00 完成，7.171 秒；不以 package 冒充测试 |
| `node --check web/pc-app/tests/privateSites.browser.js` | 退出 0 |
| 最终具名隔离 Edge fixture | 退出 0，PASS；完整原样 fixture 已由 CLI 回显，回执如下 |
| `git diff --check` | 源码/测试静态检查退出 0；提交前包含本报告再检查 |

Maven 的 18 项 MySQL 测试明确由 `isolated.mysql=true` opt-in，未设置、未读取其凭据或启动数据源；另 2 项 Windows symlink assumption 跳过。没有连接生产或合成 MySQL，不把跳过说成数据库业务/ACL 现场验收通过。成功构建只输出日志，没有 Beep、Toast、Popup 或自动编译 hook。

开发失败原记录保留：第一次完整 Node 与 shared 重建错误并行，读取到 tsc 尚未由 Vite 完成打包的临时 dist/index.js，出现 ERR_UNSUPPORTED_DIR_IMPORT；结束构建后完整重跑 296/296，最后新增注销竞态覆盖后完整重跑 298/298。新 SFC 注销测试首次缺少图标依赖 stub，1 项 ReferenceError；仅补齐测试依赖后 31/31，未删断言。其余最终检查均如上。原始 stdout/退出码由本 run 控制器完整保存在 `.../runs/20261016-021410-32364-0/task-ie-wealthhub-portal-session-20261015.stdout.jsonl`；Surefire 明细在本树 backend/target/surefire-reports，不自造/替换日志。

## 最终浏览器回执与源码绑定

从 `web/pc-app` 原生执行，一条命令一次工具调用，没有 cmd/chcp、嵌套 shell、重定向、shell 变量或内联 fixture 引号：

```text
npx.cmd --yes --package @playwright/cli@0.1.22 playwright-cli -s=ie-portal-final-20261016 open about:blank --browser msedge
npx.cmd --yes --package @playwright/cli@0.1.22 playwright-cli -s=ie-portal-final-20261016 run-code --filename tests/privateSites.browser.js
```

仅用 loopback Vite preview 3115 读取本次构建静态资源。全部 `/api/` 和 `/explore/` 由 context.route 的合成 fixtures 拦截，未知业务请求和外 origin 一律 abort；没有真实登录密码、服务器/数据库连接或财务请求。测试 1440/390 宽度、键盘激活、身份恢复门禁、401/403/404/503/网络/过期/无 no-store 拒绝、重试、固定跳转和去重、页面重载后的 Cookie CSRF 注销、旧 logout 离线、真实另一标签页 token 切换、延迟原身份/私有 exchange、在途退出和旧注销不清新账号。fixture 的导航目标只是 mock 边界页面。

最终未经编辑的 CLI 返回：

```json
{"result":"PASS","boundary":"ALL_BUSINESS_REQUESTS_INTERCEPTED_NO_SERVER_OR_PRODUCTION_CONNECTION","pageerrors":[],"unexpectedRequests":[],"financialWrites":0,"interceptedRequests":82,"widths":[1440,390],"matrix":["layout-keyboard-1440","layout-keyboard-390","failure-401","failure-403","failure-404","failure-503","failure-network","failure-expired","failure-cacheable","owner-fixed-destination-retry-dedup","reload-csrf-logout-offline-jwt-clear","real-cross-tab-account-switch-late-exchange-non-owner","late-original-identity-discarded","pending-exchange-logout","delayed-logout-preserves-new-account","original-identity-401-regression"]}
```

最终 fixture SHA256 运行前后相同：`c5bf2313917688fc96eb1484e73ccb037723f98c13dbfefd9fb086543d0b8593`。先前 68 请求的首轮回执对应较早源码，只作历史记录；最终依据为上面 82 请求回执。console 资源错误是刻意注入的拒绝/离线响应，pageerrors 为空。没有额外运行旧 portalAuth.browser.js（其旧路由表不认识新增私有端点）；原六项 Node 回归和新浏览器矩阵已覆盖本次门户/会话范围。未运行全站其余浏览器矩阵、Android 构建/真机、真实 Owner、Nginx/公网/生产验收。

最终源/测试 Git blob（`git hash-object`，非生产制品哈希）：

| 文件 | Git blob |
| --- | --- |
| Portal.vue | `86da96712179060b0b6f0d88c1d064f642f883fe` |
| privateSites.ts | `191fee7f090cb538f78543460cb5c508aaa0053c` |
| session.ts | `391dcf12041844f54afc824f77cfd1f93c27818a` |
| privateSites.test.mjs | `eaefbd759fd4e54bdfb00d5b02d9cad825305321` |
| privateSites.browser.js | `738c521ecd8efa36efd30fbae6675d55c670491e` |
| tsconfig.privateSites.json | `e652a8508cafb2bc01c0c158b9e94679e424e4e5` |

以上最终测试后只补本报告，不再修改已验证源码/fixture。本地提交 SHA 由 Git 原输出/控制器绑定，本文不自引用尚未生成的 SHA。进程、浏览器和产物清理由控制器负责，没有 kill 任意进程或自制清理脚本。

## 发布边界

源码接入通过不等于生产已开启或真实 Owner 验收通过。后端 enabled/Owner 绑定、生产兼容最小 JAR、www HTTPS/可信 Origin/loopback/Nginx internal/no-store/发布 ACL 和真实 Windows/Android 浏览器验收，继续沿已交付后端与认证调查的独立批准流程；不从本任务扩大权限。IE 即时监听缺口须在其独立授权范围实现后才能声称即时全站退出。撤销入口时应禁用私有路由/会话服务并保留保护，不能回退为公开 alias；本次未执行部署或生产回滚。
