# AUTH-001 订单与人工结算作用域审计

- 日期：2026-10-13，Asia/Hong_Kong；任务 `task-mydca-auth001-order-scope-hardening-20261013`。
- 本次新鲜执行 run：`20261013-111135-36948-0`。旧救援归档只读，旧测试与交付收据不作为本次通过证据。
- 输入 HEAD、控制器基线和只读 `git ls-remote origin refs/heads/v2` 均为 `951b01c07d753fd55baaf812de15264072c81945`；未 fetch、merge、push 或部署。
- 修复位置见下表；修复提交为**包含本报告的唯一结果提交**，完整 SHA 可由 `git log -1 --format=%H -- docs/wealthhub_auth001_order_scope_audit_20261013.md` 定位。报告与实现、测试一起提交，不另建报告提交。
- **结论：本地模拟实证确认原后端对象级授权缺口，已完成最小 Service 修复与离线回归；生产发布安全门槛为 NO。** 没有证据表明漏洞已在生产被利用，本任务未授权生产发布。

## 身份、owner、family 与 role

`SecurityConfig.java:65` 要求订单/结算业务入口已认证。`JwtAuthenticationFilter.java:45` 验证 JWT 后以 username 建立 SecurityContext；`UserService.java:52` 根据认证 username 经 UserMapper 读取 id/familyId。客户端的 userId/familyId 或持有 orderId 都不是授权来源。JWT 当前未加载家庭角色 authorities，UserInfo 也没有角色字段。

订单模型及 `OrderMapper.xml:36` 仅有 `user_id`，没有订单 family_id 或家庭代办授权。既有无 status 列表、`SettlementAuditService.java:39,51` 历史审计与重复确认采用本人订单规则。本修复统一为非空 `order.userId == authenticatedUser.id`；家庭 ADMIN/MEMBER 不能读取、取消或结算他人订单。订单本人仍可用已有个人/家庭资金账户可见性，即账户 ownerUserId 匹配本人，或 ownerFamilyId 匹配服务端数据库用户的 familyId；未扩张角色权限、未引入新金融语义或 DDL。

测试采用虚构 owner 1/2、不同 family 10/20、同 family 对照和 ADMIN/MEMBER/VIEWER authority 标签。角色标签只用于证明不能覆盖对象归属，不代表生产家庭角色表或真实 JWT 登录全链路验收；本系统没有新增“本人 VIEWER 禁止操作”的角色规则。合法 ADMIN/MEMBER 本人详情、取消与本人使用家庭资金账户的 preview/confirm 正常。

## 修复前实证与修复对应

先复用已审查的两份允许范围内测试，在**仍为输入版本的业务源码**上重新执行；随后永久应用经 diff 审查的两处 Service 修复。未通过 git show、checkout/reset/restore 或临时覆盖源码做比较；控制器隔离基线树未编辑、未重跑。

| 编号 | 输入版本证据与本次新鲜复现 | 影响 | 最小修复及复核位置 |
| --- | --- | --- | --- |
| AUTH-001-A | 原 OrderService:732 → OrderMapper.xml:32 只有 status；HTTP status 查询期望 1 行实际 2 行 | 其他 owner 订单泄露；pending 与 Dashboard 复用缺口 | OrderService:725,731 用当前 user_id 的既有 SQL，再过滤状态并保留 expectedConfirmDate 升序/null 优先；OrderScopeSecurityTest:74,90 |
| AUTH-001-B | 原 OrderService:746 按 ID 直接返回；外域详情期望 404 实际 200 | 订单、资金行、结算及账本可能泄露 | OrderService:754 在结果返回前核 owner；783,804 关联 helpers 独立核 owner；OrderScopeSecurityTest:123,159 |
| AUTH-001-C | 原 OrderService:641 仅核 PENDING；外域 cancel 期望 400 实际 200 | 越权进入恢复余额、删除流水和修改状态路径 | OrderService:644 先 requireOwnedOrder，后状态及任何关联读取/写入；OrderScopeSecurityTest:105,203 验证副作用仓储零调用 |
| AUTH-001-D | 原 SettlementService:188 先向计算结果填入外域订单；终态 preview 泄露 productId=101；可见资金账户的外域订单返回 confirmSupported=true | 元数据泄露；账户可见性被误用为他人订单权限 | SettlementService:197 在 DTO/状态/资金读取前核 owner，139 包括幂等重放先核 owner；OrderScopeSecurityTest:112,123；SettlementServicePreviewTest:535 |
| AUTH-001-E | 原 SettlementService:400,517 全局按产品选择 linked account 未核 owner；外域 linked account fixture 返回 confirmSupported=true | 本人订单也可能影响外域关联账户 | SettlementService:399,519 在关联账户计算之前核个人/家庭可见性；SettlementServicePreviewTest:549 覆盖 BUY/SUBSCRIPTION/SELL/REDEMPTION |

所有实证均来自 MockMvc → 真实 Controller/Service → Mockito 仓储，没有启动后端服务器、数据源或真实交易服务。

## 逐 API 权限、状态与幂等矩阵

路径均以 `/api/v2` 为前缀。所有入口身份均来自上述服务端认证上下文；orderId 来自路径或请求 DTO。家庭/角色标签不跳过订单 owner 校验。

| API / Controller 位置 | Service → Mapper / 对象授权时机 | 状态、幂等与合法业务语义 | 外域/不存在/伪造身份及副作用边界 |
| --- | --- | --- | --- |
| GET `/orders`（无 status），OrderController:78 | getOrdersByUserId:743 核参数等于当前 id → selectByUserId；XML:36 的 user_id 条件 | 本人所有状态，created_at DESC | 请求 userId/familyId 不参与授权；Service 传外人 id 也拒绝；只读 |
| GET `/orders?status=...`，OrderController:78 | getOrdersByStatus:731 先按当前 user_id 查，再筛 status | 状态日期排序保持，null 最先 | 不调用全站 selectByStatus；只读 |
| GET `/orders/{orderId}`，OrderController:92 | getOrderByOrderId:754 核 owner 后才返回；资金行/结算 helpers 再核 owner，Controller 随后查 ledger | 本人任意状态的原详情和实际费用计算 | 外域/不存在统一 404；无资金行、结算、账本读取 |
| POST `/orders/{orderId}/cancel`，OrderController:226 | cancelOrder:642 → requireOwnedOrder → PENDING → 关联 Mapper | 本人 PENDING 才能取消；CANCELLED/CONFIRMED/FAILED 拒绝；原取消业务算法保留 | 外域/不存在在关联读取/写入前拒绝；既有异常处理映射 400；无恢复余额、删除或 update |
| GET `/settlements/pending`，SettlementController:62 | getPendingOrders:724 → 当前 user_id SQL → PENDING | 本人待结算，DashboardService:62 同样受保护 | 不读全站订单；只读 |
| GET `/settlements/history`，SettlementController:48 | 原 SettlementAuditService.history:39 → 当前 user_id SQL → 仅本人有结算订单 → build | 既有历史对账只读事务 | 原授权已安全，未修改；HTTP/Service mock 回归 |
| GET `/settlements/history/{orderId}`，SettlementController:53 | 原 SettlementAuditService.detail:51 先核 owner，再读取 settlement/build | 本人有结算才返回详情 | 外域/不存在/无结算返回 404；外域关联仓储无调用 |
| POST `/settlements/preview`，SettlementController:77 | previewSettlement:123 → runSettlement:174：非空 ID → owner → PENDING → 尚无结算 → 全部资金账户及 linked account 可见 → 参数/计算 | 日期非空，净值>0，份额/金额/费用非负；卖出/赎回确认金额>0；保留 freshPreviewToken | 外域/不存在返回 confirmSupported=false 与通用 blockingReasons；无外域产品/方向/状态/令牌；所有 preview 无业务写入 |
| POST `/settlements/confirm`，SettlementController:99 | confirmSettlement:139 先核 owner，再读幂等结果；未结算走相同 runSettlement，1032 比对 fresh token 后写入 | 本人重复确认返回既有记录，不重复账本；新确认须 PENDING/合法账户/参数/最新指纹；保留 @Transactional | 外域先于 settlement 读取拒绝；无令牌/偷取/过期令牌均不能写入 |
| POST `/orders/{orderId}/settle`，OrderController:240 | 路径 ID → 同一 confirmSettlement | 与 confirm 完全相同的后端门禁 | body 伪造 userId/familyId 无效；外域 HTTP 回归拒绝且零写入 |

随机/不存在、空白、owner=null 订单均不能返回外域信息或产生副作用。相邻 POST `/orders` 创建和 `/orders/calculate-fee` 不属于本次既有订单访问/取消/结算验收，未执行真实下单、真实取消或正式记账。

## 状态、缓存、事务与并发

未修改金额/份额精度、手续费、金融计算、账本模板、预览指纹算法、事务注解或状态迁移；BUY/SUBSCRIPTION 不重复扣下单现金、SELL/REDEMPTION 人工结算才落账的既有测试保留。订单的家庭资金账户使用不变，已通过合法家庭资金 fixture。

Service 不缓存授权结果，相关订单/结算 XML 无二级 cache 声明；按 ID 仓储结果每次均重新比较当前 owner。相同 Service 实例 owner-A → owner-B 切换后列表、详情、历史、幂等确认重新鉴权；旧令牌不能跨身份使用。两线程独立 SecurityContext 并发外域 cancel/confirm 均被拒绝，账户/账本/结算/订单副作用仓储零调用。

既有 @Transactional、fresh preview 比对和 `sql/initsql/DDL.sql:396` 的 settlement_confirm.order_id 唯一键均未改变。**两个合法 confirm、合法 cancel/confirm 竞争、真实 DB rollback 与锁隔离 NOT_RUN**：没有任务已批准的隔离数据库 fixture。Mockito 的异常传播与重复确认测试不能证明真实数据库回滚。现有流程在插入结算确认之前写账户/ledger，并发失败者的全部回滚需要同一真实事务及已部署唯一键；不能用生产补测。

## 本次验证命令与真实结果

原始命令和完整输出由控制器记录在 run `20261013-111135-36948-0` 的 `task-mydca-auth001-order-scope-hardening-20261013.stdout.jsonl`。以下均为本次执行，未借用归档的旧成功收据。

| 验证 | 命令 | 退出码 / 结果 |
| --- | --- | --- |
| 原源码复现 | `mvn.cmd '-Dtest=OrderScopeSecurityTest,SettlementServicePreviewTest' test` | 1；44 项，13 failures、1 error、0 skipped；19:16:03 +08:00 |
| 增量编译尝试 | `mvn.cmd '-Dtest=OrderScopeSecurityTest,SettlementServicePreviewTest,SettlementAuditServiceTest,SecurityConfigTest' test` | 1；51 项，13 failures、1 error。Copy-Item 保留归档旧时间戳，Maven 输出 Nothing to compile，使用了原字节码；该次不能作为修复后验证 |
| 修复后干净编译 | `mvn.cmd clean '-Dtest=OrderScopeSecurityTest,SettlementServicePreviewTest,SettlementAuditServiceTest,SecurityConfigTest' test` | 0；重新编译 170 源码/39 测试文件，51 项全通过；19:18:14 +08:00 |
| 最终全后端 | `mvn.cmd test` | 0；288 项，0 failure/error/skip；OrderScopeSecurityTest 16 项、SettlementServicePreviewTest 32 项；19:19:38 +08:00；含新增排序与四方向关联账户测试 |
| 前端依赖 | `npm.cmd ci --ignore-scripts --no-audit --no-fund`（web） | 0；156 packages；锁文件未改 |
| shared | `npm.cmd --prefix web/shared run build` | 0；tsc、声明生成与 Vite build 通过 |
| 最终 PC 全量类型 | `node web/node_modules/vue-tsc/bin/vue-tsc.js --noEmit -p web/pc-app/tsconfig.json` | 工具返回 1；44 条既有诊断，与控制器基线逐条人工对照，文件/行列/代码/文字一致；不声称类型通过，不将包装码 1 当作基线原生码 2 |
| 完整 compile hook | `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0` | 源码阶段与含报告阶段均退出 0，Build passed；完整 backend package + shared/PC/mobile build；本次报告收据补全后提交前再运行同一命令 |
| 浏览器 | 下述 named session 与原 fixture 命令 | 0；PASS；57 拦截请求；pageerrors/unexpectedRequests=[]；financialWrites=0 |
| 暂存一致性采集 | `python C:/Users/TimeL/.codex/skills/commit-branch-consistency-review/scripts/collect_review_evidence.py --repo . --mode staged` | 0；2 Service + 2 测试；原生 Git 367 insertions/24 deletions；采集器换行统计膨胀不能替代 Git diff |
| 最终 5 文件采集 | `python -X utf8 C:/Users/TimeL/.codex/skills/commit-branch-consistency-review/scripts/collect_review_evidence.py --repo . --mode staged` | 0；2 代码/2 测试/1 报告；无配置、依赖或脚本变更，UTF-8 输出可读 |
| 原提交 validator 预检 | `python -X utf8 C:/Users/TimeL/.codex/git-hooks/commit_branch_consistency_review.py --repo .` | 0；原 validator 明确输出“提交完整性审查通过：暂存区 5 个文件”；只有 1 个已人工核实的疑似脚本引用 warning；实际 git commit 仍自动执行此原 hook |

完整 hook 未使用 BackendOnly/WebOnly；MaxFixRounds=0 只阻止自动修复子进程越过 allowed_paths，不改 hook、不省略构建步骤；后端全测试独立执行并通过。hook 原有非侵入成功行为保持，未修改声音/通知设置。

唯一类型基线为用户指定的 `D:\Project\ai-core-runtime\agent-runtime\dispatch\dual-worker-state\runs\20261013-111135-36948-0\controller-baseline\baseline.json` 与同目录 `types-baseline.log`。基线 type_exit_code=2、type_error_count=44；日志 SHA256 再核仍为 `da3078b9267dc499733e483a4e702346229d891167fa4e019245010924adce32`。仅只读报告/日志，没有编辑基线树、重跑基线或替换源码比较。全量类型无新增、删除或位移，但原 44 条仍未解决。

浏览器按 playwright 技能及用户指定命令，在 web/pc-app 直接执行：

```text
npm.cmd run dev -- --host 127.0.0.1 --port 3114 --strictPort
npx.cmd --yes --package @playwright/cli@0.1.22 playwright-cli -s=auth001-clean-20261013 open about:blank --browser msedge
npx.cmd --yes --package @playwright/cli@0.1.22 playwright-cli -s=auth001-clean-20261013 run-code --filename tests/ordersSettlements.browser.js
```

仅 Vite 提供本地 UI 静态资源；没有后端服务器。原 fixture 在导航前 route 全部业务 HTTP，未声明方法/接口拒绝，外域 abort，仅使用 offline-fixture-only 虚构 token。最终原始 run-code 输出完整回显未变 fixture，并返回：

```json
{"result":"PASS","pageerrors":[],"unexpectedRequests":[],"financialWrites":0,"interceptedRequests":57,"previews":4,"confirms":2,"boundary":"ALL_BUSINESS_REQUESTS_INTERCEPTED_NO_SERVER_OR_PRODUCTION_CONNECTION","screenshots":"output/playwright/orders-preview.png + settlements-{1440,1100,760,dark}.png"}
```

fixture 的 409/500 用于模拟旧预览和读取失败，不是业务请求漏拦截；浏览器证明前端预览/二次确认/失效清除/历史契约，Java 授权证据来自独立 MockMvc。进程清理由控制器负责，没有任意杀进程或自制清理脚本。

## 交付一致性审查

采用 commit-branch-consistency-review 的 staged 变更审查，最终交付 5 文件：2 个 Service、2 个测试、本报告。未修改 Controller、Mapper/XML、DTO、schema、配置、依赖、SQL 或调度。不是全分支审查，不声称全量 Mapper/SQL 数量闭环。

| 分类 | 变更与配套关系 | 结论 |
| --- | --- | --- |
| 功能/接口 | 同一路由与响应形状，通过 Service 限制他人订单；PC/mobile/Android 现有请求无新增参数；10 个入口矩阵齐全 | 本次离线回归通过 |
| 代码/测试 | OrderService、SettlementService 对应 HTTP owner/identity 切换/线程边界及家庭资金/关联账户/状态/旧 token 测试；中文注释说明授权先于副作用 | 通过；未加入记录敏感财务数据的新日志 |
| 脚本/数据库 | 复用现有 user_id SQL，既有事务/唯一键不变，无新表/字段或 migration | 本变更无需数据库交付；真实 DB 事务验收待确认 |
| 配置/依赖/构建 | 无新配置/依赖；JUnit 参数化测试来自已有 starter-test；原构建入口不变 | 通过 |
| 文档/交付 | 同一个结果提交包含实现、测试与最终报告；提交自动执行原 pre-commit validator | 最终 validator/Finalizer/通知状态由控制器收据判定，不手写 Delivery |

采集器报 `OrderService.java:248` 引用缺失 sourceLines.sh；人工核实原文为注释 `Σ(sourceLines.shares)`，是份额公式而非脚本依赖，属于启发式误报。首次采集 stdout 中文编码有损，最终以 Python -X utf8 重新采集全部 5 个暂存文件恢复可读输出；不以乱码或脚本统计作业务证据，实际 diff、引用位置及文件清单独立核对。原 validator 同样将该启发式信号降级为 warning 并通过。

| 编号 | 等级 | 位置与证据 | 影响 / 所需受控复核 |
| --- | --- | --- | --- |
| RELEASE-001 | NEEDS_CONFIRMATION | SettlementService:138,1087；真实事务代理与唯一键未在 DB 验证 | 合法并发失败者应全部回滚；需 owner 批准的隔离 DB/schema fixture；本任务 NOT_RUN |
| RELEASE-002 | NEEDS_CONFIRMATION | JwtAuthenticationFilter:45、UserService:52；mock authority 标签未接生产角色表 | 真实 JWT/家庭 ADMIN/MEMBER/角色变更与设备路径需隔离账号验收；不能用真实凭据补测 |
| RELEASE-003 | WARNING | 控制器 types-baseline.log 与最终 vue-tsc 输出均 44 条诊断 | 没有新增类型问题，仍不能宣称全量 TS 通过；需单独前端任务解决既有诊断 |

本次对象授权修复的变更一致性审查为**有条件通过**，生产发布结论仍为 **NO**。不新增家庭代办能力；如确需代理他人订单，应另行明确授权策略/数据模型。

## 源码与测试 SHA256

下列为实际文件字节 SHA256，Git 换行归一化后的 blob 标识不同。

| 文件 | 输入源码 SHA256 | 本次最终 SHA256 |
| --- | --- | --- |
| backend/src/main/java/com/timelordtty/dca/service/OrderService.java | cb5055cf90e7525f804c33d5bb11c9906e62cdbf7d3801fd050cec1964ab96f3 | 6f5a501f5e2b878bb83e4a8dc6a025540547c4e52be95434018137f81cef5f60 |
| backend/src/main/java/com/timelordtty/dca/service/SettlementService.java | 47f7ad239be84d9b5ef24c132aea43a1242516693b6ed6dd81bd2e4230897bce | 4230e8a55dfbac4f9d596e90678b57eadf2149281cab7e2cbe935dc5cd41137d |
| backend/src/test/java/com/timelordtty/dca/service/OrderScopeSecurityTest.java | 新增；复现阶段为已审查归档测试，后补列表排序 | f31f3e84c9343ffd29deaf88de4136fed7d8e6b32befa4eb0c4163f585d83683 |
| backend/src/test/java/com/timelordtty/dca/service/SettlementServicePreviewTest.java | 输入测试未单独记录，不伪造 | 1590ed87cbaf8d2c7f57b7f367bdfa8be519453c9a4890120db5213b84e159b4 |
| web/pc-app/tests/ordersSettlements.browser.js | 968e9a9ed6d440ad52180185392f0888af1c643a029b8c2d062ad069e39739e3 | 同左，未编辑 |

## 安全与最终交付边界

所有源码/测试/报告修改均在 allowed_paths。没有编辑 task 文件、救援归档、控制器/锁/Goal/approval 或基线 worktree，没有自动下单、真实取消/结算/入账/转账/交易、迁移或生产部署；没有连接生产服务器/数据库、读取真实财务记录或使用真实登录凭据。真实 financialWrites=0。

原 validator 的 5 文件预检已通过，提交时自动再次运行；AiCore candidate gate、Finalizer 与独立通知由控制器继续处理，本报告不代替正式 Delivery 或 Goal 验收。报告保存后重跑完整 hook，通过后只创建一个本地结果提交，不 push。


## 2026-10-14 独立 Work 恢复补充

昨晚 run 的原始 stderr 为 rollout 记录失败，候选门禁拒绝；旧测试通过不代表正式交付。本次 owner 明确授权同任务独立审核和隔离重建，原 failed 记录、stdout/stderr、旧树均完整保留，未知错误未加入自动放行白名单。

恢复 run `20261014-owner-work-auth001-03` 从实时 v2 `4fa39027baf845ae5c811de7b685ea78f4b705e9` 新建隔离树。逐文件复核两个 Service、两个测试和本报告，只复用 Git 已审查源码，不复用旧执行通过结论。服务端身份来自 JWT SecurityContext → UserService 当前数据库用户映射；订单只授权当前 owner，ADMIN/MEMBER 和同家庭不授予他人订单代办权；资金账户继续使用 owner/family 可见性。

新建独立负向 fixture 树只加入已审查测试，在未修复源码上 clean test 复现越权回归失败；随后对本隔离候选执行 Maven clean test、完整原编译 hook 和 diff/report/源码哈希检查。另在旧候选源码上新增 OrderCancelAccountScopeTest 实证本人订单的外域资金/账本账户取消未拒绝：3 项中 2 项失败、合法家庭路径通过。本次最小补丁复用 OrderService 既有可见性规则，任何写入前先核全部资金行及账本分录账户，保留原余额恢复算法；新增测试均在 allowed_paths。实际命令、退出码、测试数量与日志 SHA256 以 AiCore `reports/work-rescue/20261014-auth001-followup.md` 及附件为准。本段记录恢复方案，不预先宣称测试通过或通知完成。

生产发布仍为 NO：真实数据库合法并发/回滚、真实 JWT 家庭角色端到端和既有 44 条 PC 类型诊断仍不在本次完成范围。没有生产数据库/服务器连接、真实资金写入、交易、SSH 或部署。
