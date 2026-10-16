# 无限探索：现有 JVM 私有会话与内部文件授权

日期：2026-10-16，Asia/Hong_Kong。任务 `task-ie-wealthhub-private-session-20261015`。本次是默认关闭的仓库实现及合成验证；未部署、未绑定真实 Owner、未连接生产数据库、未修改真实财务记录、未交易、未推送。正式 Delivery/通知由原 Runtime/Finalizer/Hermes 决定。

## 输入、范围与证据来源

先读取原 pending 任务和仓库 AGENTS.md、docs/DOCUMENT_INDEX.md、AGENT.MD、CURRENT_DEVELOPMENT_STATE，以及原 JWT、安全链、用户服务/Mapper、账户控制器。输入是控制器创建的干净 detached task worktree，基点 `34c67f32129e58d3d24180082eb878304ee797e5`，目标独立分支 `ie-private-auth-20261015`；没有同步/合并 v2 或编辑 live checkout。

读取 AiCore 认证调查和 `wealthhub-explore-x-accel.nginx.example`、原 forest.py 及正式 B 适配器 `agent-runtime/infinite_exploration/{artifacts,publisher,protection}.py`。B Delivery 为 completed，结果 `b284263261bceb7a3d9637caf6aee65530f2885b`。读取的 AiCore main 本地输入 `7f58d9aa23d8cd7ca8ec50da79dceb6200b40057`，随后原生 `ls-remote` 观察到 `9a983cd0ed9e986a8b98d35cb6e9a4c4652da7dc`；两提交之间对上述发布适配器、认证调查及 X-Accel 模板的定向 Git diff 为空。没有改适配器、原 forest.py、其隔离来源树或私人数据。

活动控制器 run：`20261016-005545-16528-0`，原任务 SHA256 `d6beef5d6eb72c641a5be82b0760489b16c32cd93775a9683c29bedd09d7a0b7`。其 `controller_baseline=null`，比较结论为 **BLOCKED_MISSING_CONTROLLER_BASELINE**。未借用历史基线、创建自定义基线、重跑基线检查或替换当前源码做比较。本文只报告本候选实际检查，不能由类型检查通过推导历史诊断变化。

## 会话接口和身份边界

| 原生接口 | 请求要求 | 成功结果 |
| --- | --- | --- |
| POST `/api/v2/private-sites/session/exchange` | 原已验证 Bearer；精确 Origin；`X-Private-Site-Request: 1` | 200；随机 Cookie；最小状态及 CSRF |
| GET `/api/v2/private-sites/session` | 私有 Cookie；自定义请求头；精确 Origin，或 same-origin Fetch Metadata 加同源 Referer | 200；`authenticated`、`csrfToken`、`expiresAt` |
| POST `/api/v2/private-sites/session/logout` | 私有 Cookie；精确 Origin、自定义请求头；`X-CSRF-Token` 与该会话匹配 | 204；撤销服务器会话并清 Cookie |
| GET/HEAD `/api/v2/private-sites/explore` 及白名单子路径 | 私有 Cookie、可信本机代理、启用的只读发布根及 v1 | 302 固定 UI，或空响应和 `X-Accel-Redirect` |

所有新路径先检查：真实 remoteAddr 必须是精确 IPv4/IPv6 loopback，Host 单值且为 `www.timelordtty.cn`，`X-Forwarded-Proto` 单值且为 `https`。未知 Host、外部后端直连、缺失代理协议头、跨站/same-site Fetch Metadata、非可信 Origin、客户端身份断言均拒绝。`X-User`、`X-Authenticated-User`、`X-Accel-Redirect`、`Forwarded`、`X-Forwarded-Host` 出现即拒绝，不能提供身份。重复 Origin/引导头及 exchange 的重复 Authorization 同样拒绝。

新链 `@Order(1)` 只匹配 `/api/v2/private-sites`，复用原 JwtAuthenticationFilter；边界 Filter 在该链内直接创建，既不是 Component 也不是 Servlet Filter Bean。Cookie 不写入 Spring Security 身份。原 SecurityConfig/AuthController、登录注册、JWT 生成/签名/密钥和既有验证代码完全未改。JwtTokenProvider 只新增使用同一签名/到期校验的到期时间读取方法。

UserMapper 只做 `selectByUsername`/`selectById`；每次会话使用都重新检查真实用户存在、启用、精确 ID 和用户名。角色、家庭管理员、名字叫 admin 都不能代替 Owner。有效非 Owner exchange 返回 403，匿名/坏签名/过期/缺失 exp/不存在或禁用账号返回 401。私有文件只认 Cookie；混入 Authorization 的会话读取/文件请求拒绝并撤销所带会话，客户端应先显式重新 exchange。无凭据、过期、撤销的 Cookie 返回 401。关闭或缺少配置返回 503，非法来源返回 403，未知文件返回 404，发布完整性失败返回 503，不重定向成登录 HTML。

Cookie 为 `__Host-PrivateSite`，Secure/HttpOnly/SameSite=Lax、Path=/、无 Domain，是浏览器会话 Cookie；服务器独立控制期限。ID 来自 SecureRandom 32 字节，服务端只保存其 SHA256 索引、账号标识、随机 CSRF 和到期时间，不保存原 JWT/密码，也没有凭据日志和包含秘密的生成式 toString。响应不包含会话 ID/JWT。GET state 不延长 TTL。

默认容量 128、TTL 900 秒；硬上限为 1024 会话/3600 秒。同步创建防止并发突破容量，满额返回 429；每次操作最多扫描固定容量并清过期项，无新线程/定时器/数据库/用户表。期限取 JWT 到期和配置 TTL 的较早者。重启丢失全部会话，现有有效 Bearer 可重新 exchange。同源 exchange 先撤销当前浏览器旧 Cookie，即使切成非 Owner/无效身份也不保留旧 Owner 会话。其他浏览器独立会话保留。

exchange 的 CSRF 引导由精确 Origin 和非简单自定义头共同保护，跨域预检无放行头；logout 再要求不可猜测的会话 CSRF。GET 接续也要求自定义头与同源证据。此链关闭 Spring 默认 CSRF 的原因仅为采用上述独立机制，原金融链的行为没有扩大。

本任务只交后端。门户/Android 浏览器一次登录后自动 exchange、旧 logout 前调用私有 logout、账号切换钩子、跨标签即时清空，属于独立门户接入任务；现有 `/auth/logout` 不会自动撤销这些新会话。未假称当前生产已具备一次登录或跨标签 SSO。

## 配置、发布格式与实时数据

| 配置 | 默认值/生效要求 |
| --- | --- |
| `private-sites.enabled` | false；必须显式开启 |
| `private-sites.owner-user-id` | 无；必须另批绑定真实有效 ID，不推断/写入真实账号 |
| `private-sites.session-capacity` | 128；只接受 1–1024 |
| `private-sites.session-ttl-seconds` | 900；只接受 1–3600 |
| `private-sites.publication-root` | 无；绝对私有根，禁止放在任何公共 root 中 |
| `private-sites.protocol-version` | 0；文件授权须显式设为 1 |

只有前四项满足才启用会话；只有完整配置、current 和 release 校验通过才提供文件。根、releases、current.json 及所选 release 所有目录/文件对 JVM 有效身份均不得可写。写权限检查是真实 `Files.isWritable`，不是一个声称只读的布尔开关；生产必须使用经批准的独立发布身份与 ACL/只读挂载，JVM/Nginx 不能持有发布写权限。

严格复用 B v1 指针：`schema_version=1`、`current`、`previous`、`high_water_source`、正整数 `generation`，不猜 latest 目录。release 目录名是 release.json 原始字节 SHA256；描述绑定 repo_id、target_main_sha、ui_source/data_source、protocol_version=1、approval_ref、previous_release、七文件 bytes/hash 和两次成功 native verify 的日志哈希。UI/data 来源可以不同，不重新构建 UI，也没有新 CI/候选库。

每次选择检查真实路径各层，不跟随 symlink、other/reparse 类型或 realPath 改向；POSIX 支持时额外拒绝多硬链接。解析拒绝重复 JSON 键、尾随 JSON、非整数/溢出协议；检查干净源标记 false、来源 SHA、精确目录/文件集合、文件 bytes/SHA256、快照协议/revision、私有标记和原生验证记录。UI/元数据上限 4MiB，快照 20MiB。文件读取前后复核类型、fileKey、大小和修改时间，哈希流有硬上限。没有任意文件路径、目录清单或完整文件响应体。

公开授权白名单只有四 UI 文件 `index.html`、`style.css`、`forest-model.js`、`app.js`，`data/manifest.json` 和 `data/snapshot-<sha256>.json`。ui-build.json/release.json 只用于服务端验证，不向浏览器提供；staging/backup/queue/log/.git 永远没有公开别名。原始 URI 精确匹配，百分号编码、二次编码、反斜线、dot segments、重复斜线、未知版本/文件均不映射。

`/explore`、`/explore/` 和未固定版本的 UI URL 302 到 `/explore/r/<current>/...`，避免指针切换时混合 UI 文件。固定 UI 的相对 `data/manifest.json` 每次重新读取 current，只有 v1 且四 UI 文件哈希完全一致的发布才可作为兼容数据；这比仅看协议号更保守。返回内部 URI 指向当前发布的原始 manifest，不改写其内容或 B 指针。数据-only 发布无需构建 UI。

哈希快照按完整 hash 选择当前、固定版本或受保护的保留版本；历史选择最多检查 256 个 release 条目，超过上限拒绝，不回退任意文件。相同 UI 可以从新 manifest 获取新快照；连续两次以上切换后，旧 hash 仍可从相应不可变 release 读取。UI 不兼容则 503，要求重载经验证的新 UI。没有把旧不可变 manifest 当作 90 秒实时刷新。

哈希只证明完整性，不证明来源可信。完整内容 revision/索引/领域语义仍由 B 的固定 Git 原生发布验证负责，Java 不另造 Python 语义引擎或执行命令；安全前提是 publication-root 仅由批准的 B 发布链写入且不可变。Java 与 Nginx 间的字节不可变/目录不可替换由 OS ACL 保证，不能拿哈希代替 ACL；Windows Java 没有通用 nlink 视图，Windows 硬链接前置检查继续依赖 B。

## Nginx 契约（仅供审查，未安装）

无需 auth_request 模块、新 JVM、DNS/证书变化或客户端证书。未来批准安装时，须在既有 www HTTPS server 使用认证调查的 X-Accel 模板，并满足以下精确约束：

1. `/explore` 与 `/explore/` 的 GET/HEAD 代理到对应 `/api/v2/private-sites/explore`；不配置公共 alias 回退。入口验证 HTTPS、Host 和 TLS SNI 均精确 www。原金融 `/api/` 不改认证语义。
2. 会话路径 `/api/v2/private-sites/session`、`/session/exchange`、`/session/logout` 转发原 Bearer、Cookie、Origin、Referer、Fetch Metadata、X-Private-Site-Request 和 X-CSRF-Token；新路径覆盖 upstream Host 为 www、X-Forwarded-Proto 为 https，清掉上述五种不可信断言头。公共 `/api/v2/private-sites/explore` 入口应拒绝，文件只经 `/explore` 映射。
3. 后端只绑定/允许可信 loopback Nginx，公网 Java 端口必须封闭。容器 `server.forward-headers-strategy=none`，不能把来客 Forwarded/X-Forwarded-For 改写成可信 remoteAddr；JVM 依据真实连接地址。loopback 本机进程属于部署信任边界，不能声称防御已被攻陷的本机进程。
4. 内部文件位置必须 `internal`，仅允许 `/_private_ie/<64hex>/(ui/(index.html|style.css|forest-model.js|app.js)|data/(manifest.json|snapshot-<64hex>.json))`，映射同一 publication-root 下 `releases/<id>/<file>`；没有 release.json/ui-build.json 或任意通配文件。外部访问该位置必须 404；其他 `/_private_ie/` 路径拒绝。
5. `disable_symlinks on`、autoindex off、limit_except GET HEAD、max_ranges 1。Nginx 实际处理 HEAD/Range/206；Java 仅在每次授权后返回内部 URI，保留原 method/Range。Java MockMvc 不能替代真实 Nginx Range/SNI/内部位置验收。
6. 新代理显式 `proxy_cache off`、`proxy_store off`，所有 200/206/HEAD/302/401/403/503 均 `private, no-store`，内部位置也设置 no-store/nosniff 和调查模板 CSP。超时/认证错误不得进入公共静态根；不得继承全局共享缓存。

没有执行 nginx -t/reload、服务器目录创建、网络防火墙或任何生产命令；本报告不是可直接发布的批准文件。

## 实际测试与构建

原生完整命令均一条命令一次工具调用，无 shell 重定向、临时源码覆盖、shell 嵌套、自制清理或编译 hook。原始输出保存在控制器 `agent-runtime/dispatch/dual-worker-state/runs/20261016-005545-16528-0/task-ie-wealthhub-private-session-20261015.stdout.jsonl`；Surefire 原生明细在当前工作树 `backend/target/surefire-reports/`。这些由控制器持有，不复制私人快照或创建假日志。

| 检查 | 实际结果 |
| --- | --- |
| `mvn -f backend/pom.xml test` | 最终退出 0，374 项，0 失败/错误、20 skipped；2026-10-16 09:14:19 +08:00 完成，2:56 min |
| `mvn -f backend/pom.xml -DskipTests package` | 退出 0，BUILD SUCCESS；09:16:47 +08:00 完成，6.537s；没有以 package 冒充测试通过 |
| `npm.cmd --prefix web ci --no-audit --no-fund` | 退出 0，按原 lockfile 安装 156 包；未修改锁/依赖声明 |
| `npm.cmd --prefix web run build` | 退出 0；shared、PC、兼容 H5 均构建成功；保留 npm 配置/大 chunk 警告，无声响/弹窗 |
| `node --test --test-concurrency=1 web/pc-app/tests/*.test.mjs` | 最终退出 0，273/273 通过，无跳过 |
| `npm.cmd --prefix web/pc-app run type-check` | shared 已构建后的最终检查退出 0，无诊断；无基线比较结论 |
| `npm.cmd --prefix web/shared run type-check` | 退出 0，无诊断 |
| 原样浏览器 fixture | 具名隔离 Edge `ie-private-session-20261016`，先 about:blank，再指定原生命令；退出 0，PASS；详见下文 |
| 静态检查 | `git diff --check` 退出 0；原 SecurityConfig、JwtAuthenticationFilter、AuthController 和 web/package-lock.json 对输入基点的定向 diff 为空；11 个变更路径均在 allowlist |

原测试的数据库边界先读代码确认：`IsolatedMysqlBusinessTest`（17）和 `IsolatedMysqlEnvironmentTest`（1）均须显式 `-Disolated.mysql=true`。本任务未设置该开关、未启动它们的数据源，18 项保持 opt-in 跳过；没有恢复原 B MySQL，更没有冒称原 B 真实 311 项验收通过。新测试用随机合成 JWT 密钥、虚构 ID 7001/7002 和 Mockito UserMapper/业务只读服务，不启动 Spring Boot 数据源。

本地后端 JAR SHA256 为 `5bc005c75e155df5edc4029bb8f4a27d1a4beaf1bea5cdf8d1b025c6340e666f`，只绑定本次构建，不是线上制品或部署批准。最后一次源码修改后完整 Maven 已重新通过；后续只写本报告，没有再改源码。最终浏览器 fixture 文件哈希与运行前一致。

新增测试共 63 项：52 发布契约/反例、7 真实新旧安全链、4 内存会话。安全链通过真实 JwtTokenProvider/JwtAuthenticationFilter、原 SecurityConfig、新链、新 Controller、原 UserService/UserController/AccountController；所有 UI/manifest/hash 的 GET/HEAD 和 Range 请求检查匿名拒绝、非 Owner Bearer 不能替代 Cookie、Owner 空响应及正确 X-Accel。还覆盖 Cookie、CSRF、Origin/Host/代理、重复头、坏签名/过期/无 exp、账号切换/禁用、撤销、TTL/容量并发/重启和当前 manifest 推进/历史 snapshot/不兼容 UI。原账户 API Cookie-only 仍 401，Android 原 Bearer 账户 GET 仍成功，混入私有 Cookie 也不改变其他用户的 Bearer 身份。

临时发布根对测试进程可写，合成测试仅 mock 两个 OS 只读检查方法，其余路径/JSON/字节/哈希选择全部真实执行；另有真实可写根必须 503 的测试。Windows 本进程无创建符号链接权限，文件/目录 symlink 两项由 JUnit assumption 明确 skipped。真实 junction、只读 ACL 正例及 Nginx disable_symlinks 现场验收 **NOT_RUN**，没有把 mock 或 skipped 说成通过。

第一次完整 Maven：322 项，1 failure，原因为预检请求安全拒绝 403，而测试错误预期 2xx；修正断言后完整测试通过。首次 npm build 因隔离树未装 tsc 退出 1；安装依赖后按同一原生命令成功。首次 Node/PC 类型检查在 shared/dist 尚未构建时失败；完成共享构建后两者均完整重跑通过。所有失败原输出保留，不替换/隐藏。未执行 Android 构建、真实用户认证、Nginx/公网验收或上游 E 全套。

## 浏览器原始回执

从 `web/pc-app` 直接执行，不编辑 fixture：

```text
npx.cmd --yes --package @playwright/cli@0.1.22 playwright-cli -s=ie-private-session-20261016 open about:blank --browser msedge
npx.cmd --yes --package @playwright/cli@0.1.22 playwright-cli -s=ie-private-session-20261016 run-code --filename tests/typecheckClearance.browser.js
```

`typecheckClearance.browser.js` SHA256：`b051dac048186b16a892b9ef1e98ae428053e5a9715969dc82680a8ceeeded25b`，原 CLI 输出完整回显了未改的 fixture。结果：

```json
{"result":"PASS","boundary":"ALL_BUSINESS_REQUESTS_INTERCEPTED_NO_SERVER_OR_PRODUCTION_CONNECTION","widths":[1440,1100,760],"pageerrors":[],"unexpectedRequests":[],"interceptedRequests":29,"financialWrites":0,"fixtureWrites":1}
```

只启动 loopback Vite 静态 preview（3110）；业务 `/api/` 全部由 route.fulfill/abort 拦截，外源全部 abort，无后端/生产连接或真实登录。fixtureWrites=1 是合成 500 响应，没有发至服务端。浏览器 console 的两条 error 分别是 fixture 故意注入的列表 GET 500 和提交 POST 500，pageerrors 仍为空。此回执是既有金融 UI 的隔离回归，不是私有接口浏览器 SSO/生产认证成功证据。新接口由上面的真实安全链合成测试验证。

## 变更路径、关闭与交付缺口

本次只改变以下 11 个 allowlist 路径；无 SQL、配置文件、依赖、前端/Android 或其他认证源码变更：

- `backend/src/main/java/com/timelordtty/dca/security/JwtTokenProvider.java`
- `backend/src/main/java/com/timelordtty/dca/privatesite/PrivateSiteProperties.java`
- `backend/src/main/java/com/timelordtty/dca/privatesite/PrivateSiteSessions.java`
- `backend/src/main/java/com/timelordtty/dca/privatesite/PrivateSiteSecurity.java`
- `backend/src/main/java/com/timelordtty/dca/privatesite/PrivateSiteController.java`
- `backend/src/main/java/com/timelordtty/dca/privatesite/PrivateSitePublication.java`
- `backend/src/test/java/com/timelordtty/dca/privatesite/PublicationFixture.java`
- `backend/src/test/java/com/timelordtty/dca/privatesite/PrivateSiteSessionsTest.java`
- `backend/src/test/java/com/timelordtty/dca/privatesite/PrivateSiteSecurityIntegrationTest.java`
- `backend/src/test/java/com/timelordtty/dca/privatesite/PrivateSitePublicationTest.java`
- `docs/infinite_exploration_private_auth_20261015.md`

关闭时保留私有文件保护：移除/禁用新公开入口并设 enabled=false，或移除 Owner 配置；不得改成公开 alias。进程重启清空会话；回滚保留旧 JAR/配置指纹，恢复原制品及已审 Nginx 配置，发布数据/事件不删除。本任务没有安装入口，因此无需执行生产回滚。唯一一次本地结果提交将包含上述全部源、测试与本报告，提交 SHA 由原 Git 输出/控制器绑定，本文不自引用尚未生成的 SHA。

生产尚须另批：把生产 JAR SHA `0f54b414…` 绑定可靠源码与最小发布范围；真实 Owner ID；批准发布链和只读身份/ACL；可信 loopback/防火墙/Host/SNI、Nginx 内部位置及 HEAD/Range/缓存/旧版反例；门户接入与真实 Windows/Android/跨标签人工验收。没有借此部署整套 v2、修改生产认证或启用新入口。所有自动清理由控制器负责，没有 kill 任意进程或自制清理脚本。
