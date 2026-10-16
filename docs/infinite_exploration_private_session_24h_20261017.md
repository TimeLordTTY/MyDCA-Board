# 无限探索私有会话最长 24 小时：工程验证报告

2026-10-17，Asia/Hong_Kong。任务 `task-ie-private-session-24h-20261017`。本报告记录隔离工作树中的源码修复与合成验证；本轮未部署、未推送、未连接生产数据库、未修改真实财务记录、未交易或调用真实登录。Runtime/Finalizer/Hermes 的正式集成、交付与通知由控制器处理。

## 授权、来源与范围

先读原 pending 任务及本树 AGENTS.md，再读 DOCUMENT_INDEX、AGENT.MD、CURRENT_DEVELOPMENT_STATE、F/G 专项报告、AiCore 认证调查及 P0 生产交付报告。原任务文件未编辑。前置分支门禁 Delivery `reports/delivery/task-ie-private-session-branch-gate-20261017.json` 已为 completed，结果提交 `82d6df67327f7a216991a666e27c3696a39d725a`；原登记与谱系流程未改。

控制器提供干净 detached 工作树，输入 `bb14e03df9ed5c57399542d5ab7af5493cd5ef46`，与本地 `origin/ie-private-auth-20261015` 一致。未同步/修改 v2，未切换分支或修改其他工作树。本轮只交付五个 allowlist 文件：

| 文件 | 改动 |
| --- | --- |
| `backend/src/main/java/com/timelordtty/dca/privatesite/PrivateSiteProperties.java` | 默认 TTL 86400 秒；有效范围 1–86400 秒；说明 JWT 较早到期仍优先 |
| `backend/src/main/java/com/timelordtty/dca/privatesite/PrivateSiteSessions.java` | 同一次签发仅采样一次时间，严格保证 JWT 与 TTL 较早截止 |
| `backend/src/test/java/com/timelordtty/dca/privatesite/PrivateSiteSessionsTest.java` | 时钟、TTL 边界、溢出配置与既有容量回归 |
| `backend/src/test/java/com/timelordtty/dca/privatesite/PrivateSiteSecurityIntegrationTest.java` | 真实安全链加可控会话时钟，长期阅读/截止/撤销与无 DB、无财务写入断言 |
| 本报告 | 实际验证结果与未部署边界 |

`PrivateSiteSessions` 保留 `min(当次已验证 JWT 的 exp, 签发时刻 + 配置 TTL)` 的规则，同时修正原实现多次读时钟的边界：若验证时为 T，比较上限时为 T+1ms，最终构造上限时为 T+2ms，而 JWT 到期恰为 T+86400s+1ms，旧实现会返回晚于 JWT 1ms 的截止。新增递进时钟测试已在旧实现确定性失败；现在校验 JWT 与计算 TTL 共用同一个 now/ttlExpiry，避免越过任一截止。此修正直接服务于“绝不晚于真实 JWT”要求，没有改变权限或增加续期。Controller 保留原实现。Cookie 仍为浏览器会话 Cookie：host-only、Secure、HttpOnly、SameSite=Lax、Path=/，没有新增持久化 Max-Age。服务器到期时拒绝请求，401 清除 Cookie；Cookie 留在浏览器中不构成继续授权。未向 Cookie、URL、前端事件或日志复制 JWT。

## 最长时限与安全边界

24 小时是单次私有会话的最长阅读窗口，不是主登录续期。例如主 JWT 剩余一小时，exchange 返回的 expiresAt 仅一小时后；主 JWT 后续刷新不会延长已签发的私有会话。读取 session、页面或图谱均不滑动续期，也不需要每次读取重新登录。浏览器关闭或服务重启仍可能更早结束会话。多天连续阅读的续期授权不在本轮范围。

原显式短 TTL（包括 900 秒）仍然有效；默认值变化不会覆盖部署环境显式配置。0、负数、86401、long 极值均关闭会话服务，先于到期算术与用户读取拒绝；超过 long 范围的配置通过 Spring Boot Binder 真实转换失败，不会溢出回合法秒数。

实际覆盖：

- 服务时钟推进至 900、901、960、43200、86399 秒，原会话 expiresAt 不变；86400 秒精确边界拒绝。
- 真正签名并由原 JwtTokenProvider/JwtAuthenticationFilter 验证的一小时 JWT，在 901/960/3599 秒可读，3600 秒拒绝；单元测试另有 60 秒 JWT 和 1 秒配置上限。
- 真实 SecurityFilterChain/Controller/Publication 授权 UI、当前 manifest、当前 immutable snapshot，以及连续两次发布切换后的保留历史 snapshot；GET、HEAD、带/不带 Range 均验证成功内部路径和 private/no-store。截止后均为 401，无 X-Accel-Redirect，并清 Cookie。Java 只负责内部授权；真实 Nginx 的 206/字节范围处理仍需生产验收。
- 长会话使用 16 分钟后，CSRF logout、禁用 Owner、撤销 Owner ACL、切换非 Owner、混入其他账号 Bearer 均立即撤销/拒绝；重新启用账号/恢复 ACL 不复活原会话。
- 原匿名、非 Owner、坏签名、已过期/无 exp、重复 Cookie、来源/代理/CSRF、配置关闭、重启及同步容量上限/并发测试继续执行。Cookie-only 仍不能授权原财务 API，原 Android Bearer 身份边界保留。
- 每个集成用例断言上下文无 DataSource，UserMapper 只调用两个用户读取方法，AccountService 只可能执行既有合成账户树读取，股份/家庭服务零交互。使用虚构用户 7001/7002 和随机合成签名密钥，不启动数据库。临时发布目录沿原 fixture 仅 mock OS 只读检查，其他文件/哈希/路径校验真实执行，不能替代生产 ACL 证据。

## 原生验证结果

所有命令均单个原生命令一次工具调用；没有自动编译 hook、声音、Toast/Popup、shell 嵌套/变量/重定向、源码临时覆盖或自制进程清理。原 stdout、完整退出码和浏览器原样代码由本 run 控制器保存在 JSONL，Surefire 明细在本树 `backend/target/surefire-reports/`。

| 命令 | 实际结果 |
| --- | --- |
| `mvn -f backend/pom.xml '-Dtest=PrivateSiteSessionsTest,PrivateSiteSecurityIntegrationTest' test` | 最终退出 0；30 项（20 单元、10 安全链），0 失败/错误/跳过；01:42:44 +08:00 完成，2:26 min |
| `mvn -f backend/pom.xml test` | 最终退出 0；393 项、0 失败/错误、20 skipped；01:44:51 +08:00 完成，1:59 min |
| `mvn -f backend/pom.xml -DskipTests package` | 退出 0，BUILD SUCCESS；01:45:11 +08:00 完成，6.185 秒；不以跳过测试的打包代替上行全量测试 |
| `npm.cmd --prefix web ci --ignore-scripts --no-audit --no-fund` | 退出 0，安装 156 包；锁文件未改 |
| `npm.cmd --prefix web run build` | 退出 0；shared/PC/兼容 H5 构建通过，PC 2195 modules、H5 386 modules；原 npm 配置及 chunk 体积警告保留 |
| `node --test --test-concurrency=1 web/pc-app/tests/*.test.mjs` | 退出 0，298/298，无跳过，45.047 秒 |
| `node web/node_modules/vue-tsc/bin/vue-tsc.js --noEmit -p web/pc-app/tsconfig.json` | 退出 0，无诊断；控制器基线对照见下文 |
| `node web/node_modules/vue-tsc/bin/vue-tsc.js --noEmit -p web/pc-app/tests/tsconfig.privateSites.json` | 退出 0，无诊断 |
| `node --check web/pc-app/tests/privateSites.browser.js` | 退出 0 |
| 具名隔离 Edge 原样 fixture | 退出 0，PASS，81 个拦截请求；完整回执见下文 |
| `git diff --check` / `git diff --cached --check` | 最终源码、测试、报告及暂存检查退出 0 |

失败记录保留：首条未加引号的 Maven `-Dtest` 参数因 PowerShell 解析逗号失败，未启动 Maven；改为一个带引号的原生参数后正常执行。新增测试先在未改动的 900 秒实现上运行，29 项中 5 failure、3 error：明确观察到 16 分钟读 session 401、默认值 900、expiresAt 为 15 分钟及 3601/86399/86400 秒配置 503。随后仅修改 Properties 默认值和上限，同一测试命令 29/29 通过。此回归复现只使用当时工作树源码，没有覆盖/恢复源码来做比较。

首轮全量通过后补充的 `mvn -f backend/pom.xml '-Dtest=PrivateSiteSessionsTest#advancingClockCannotMoveDayLimitPastVerifiedJwtDeadline' test` 退出 1，1 项 failure，证实旧签发逻辑在上述递进时钟下实际晚于 JWT。随后只修改签发时间采样，并重新执行最终定向/全量测试及打包；最终结果以上表为准。没有篡改/删除失败回执，也没有为测试替换源码或回退树。

完整 Maven 中 18 项 MySQL opt-in 测试仅在显式 `isolated.mysql=true` 时启用；本轮未设置该开关或读取其凭据。另 2 项因 Windows 不允许合成 TempDir 创建文件/目录符号链接，由原 assumption 跳过；已核对 Surefire 原始原因。数据库现场/Android/真实 Owner 登录、Nginx、公网及连续真实 24 小时浏览均未执行；没有把 mock、时钟推进或跳过项说成生产通过。

本地完整测试用 JAR SHA256 为 `7fcb8654a460d0516d987f91f5751322d5e3e6204c0d230c209d83ceea7c3d8f`；这是本树原生打包结果，不是生产最小 overlay、生产制品或部署批准。最终定向与全量测试之后没有再修改源码/测试/fixture，只补充本报告。

## 控制器唯一类型基线

只读使用 `D:/Project/ai-core-runtime/agent-runtime/dispatch/dual-worker-state/runs/20261016-172627-2684-0/controller-baseline/baseline.json` 及 `types-baseline.log`。报告 status=passed，base=`bb14e03df9ed5c57399542d5ab7af5493cd5ef46`；dependencies/shared-build/types-baseline 退出码均为 0，type_error_count=0、diagnostics={}。types-baseline.log 原始为空，报告 SHA256 为 `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`。

最终同命令类型检查退出 0、无诊断：**基线 0 → 最终 0，新增诊断 0**。未编辑控制器树或报告，未重跑/自建 baseline，未使用 git show、checkout/reset/restore、Python/PowerShell 临时源码替换来比较；也未用历史文档中的诊断数量代替本次基线。

## 最终浏览器原始回执

从 `web/pc-app` 直接执行：

```text
npx.cmd --yes --package @playwright/cli@0.1.22 playwright-cli -s=ie-private-24h-20261017 open about:blank --browser msedge
npx.cmd --yes --package @playwright/cli@0.1.22 playwright-cli -s=ie-private-24h-20261017 run-code --filename tests/privateSites.browser.js
```

使用本次构建的 loopback Vite preview 3115。fixture 文件未编辑，运行前后 SHA256 一致：`92970e128e2f30a8385d4afa8947d807944340bc49ab57f846b178d40bd51b89`；CLI 完整回显原样 fixture。所有业务 `/api/` 和探索导航均合成拦截，未知请求及外 origin 一律 abort；仅本地静态资源允许读取。没有使用真实凭据或后端服务。原生结果为：

```json
{"result":"PASS","boundary":"ALL_BUSINESS_REQUESTS_INTERCEPTED_NO_SERVER_OR_PRODUCTION_CONNECTION","pageerrors":[],"unexpectedRequests":[],"financialWrites":0,"interceptedRequests":81,"widths":[1440,390],"matrix":["layout-keyboard-1440","layout-keyboard-390","failure-401","failure-403","failure-404","failure-503","failure-network","failure-expired","failure-cacheable","owner-fixed-destination-retry-dedup","reload-csrf-logout-offline-jwt-clear","real-cross-tab-account-switch-late-exchange-non-owner","late-original-identity-discarded","pending-exchange-logout","delayed-logout-preserves-new-account","original-identity-401-regression"]}
```

这是既有门户/退出/切账号/错误边界回归，导航目的页为 mock，不能据此宣称真实 IE 绘图或 24 小时生产成功。长时限行为由上面的真实 Java 安全链加可控会话时钟验证。浏览器 console 的拒绝/离线响应是 fixture 主动注入，pageerrors 为空。浏览器、preview 及其他进程由控制器清理，本任务未 kill 进程。

## 后续部署阻断与交付

只读核验 AiCore `reports/work-rescue/20261016-p0-production-delivery/README.md`：该时点已部署原生产 JAR `0f54b414…` 和 11-class 认证 overlay `f93c07c7…`，生产独立会话记录为 900 秒，真实自然到期清图有历史证据。**本任务没有更新上述任何生产制品或配置，源码修复不代表线上已变为 24 小时。**

现有本地脱敏报告没有当前生产 `private-sites.session-ttl-seconds` 的独立环境变量投影，也没有真实主 JWT exp/实际有效时长证据。本轮禁止服务器/凭据操作，因此两项保持 UNKNOWN，不从源码默认 24 小时推断生产 Token 配置。如果生产环境显式覆盖为 900，换默认值后仍会 15 分钟到期，必须作为后续部署阻断核实并另行批准配置变更，不能擅改私有生产环境文件。

生产交付需另批核实当前实际配置、主 JWT 较短到期行为、兼容原财富 JAR 的最小认证 overlay 构建、源码/逐类字节/制品哈希；保留旧版本和配置指纹，明确重启窗口及回滚。HTTPS Owner 阅读超过 15 分钟、到期/退出/切账号清图，以及匿名/非 Owner/旧快照 GET/HEAD/Range 拒绝仍须目标环境验收。不发布整套最新 v2、不更改 Nginx/DNS/TLS/调度、JWT 密钥、Owner 白名单或金融权限。关闭/回滚必须保持私有文件保护，禁止回退公开目录。

最终只创建一个本地结果提交，包含上述全部源码、测试与本报告；提交 SHA 由 Git 原输出和控制器绑定，本文不自引用尚未创建的 SHA。未 push、部署或发送外部消息，正式 Goal/Delivery/通知不在本报告自报完成。
