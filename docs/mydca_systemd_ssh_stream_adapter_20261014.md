# systemd SSH 流适配器交付报告（2026-10-14）

## 同任务独立救援复验说明

旧自动候选 `2adfda9bdc2385a23dcbdfc22416d403d37b61ff` 对应 run `20261014-035554-35536-0` 最终为 failed/codex_terminal_child_cleanup_evidence_blocked。CLI exit0、旧测试和下文历史候选描述均不代表交付。原 stderr 的 rollout 记录错误和 esbuild cleanup error=5 保留，不修改错误分类。新源码在实时 v2 `a288665624da368db5f9ee89dd04c40576840d22` 的全新隔离树重建，并逐文件审核。

审查发现并修复两项原阻断：断流只报人工恢复，缺少同事务重连；readiness只绑定资产，缺少源码SHA。新协议额外要求 `source_commit` 为冻结版本的40位Git SHA，与完整JAR/PC/H5资产SHA一起进入独立 readiness；调用者必须核对该源码的构建证据，格式校验本身不证明构建来源。下文原候选字段说明应加上此字段。

同一flock会话在stop前写入root/0600受限事务标记，内容仅随机token、source_commit、原件与制品清单SHA，不含旧应用、数据库或凭据。未完成事务阻止新的普通发布。断流或会话超时保持标记，远端收尾尝试停止指定unit；客户端有界重连并重取flock，仅同token与相同版本/原件/资产证明才能接续恢复。恢复前核完整固定unit契约、所有已知文件字节及配置元数据；未知人工漂移、标记缺失或锁不可取得均为ROLLBACK_FAILED_MANUAL_RECOVERY_REQUIRED。SIGKILL、服务器重启、断电可能阻止远端收尾或清除/run标记，此时自动恢复不获授权，必须人工核验；本任务没有真实演练。

成功切换或完整恢复并通过后端JSON UP、未认证401、单实例和完整清单核验之后才清除事务标记。冷服务回滚保持原inactive。所有文件使用同文件系统单文件原子替换；整个PC/H5资产集合仍不是原子目录快照，中途失败必须从本地原件恢复完整清单后才能被接受。无法确认时不宣称新版上线或旧版恢复。release额外拒绝junction、父路径链接和文件/目录冲突，限制4096项、单文件256MiB、合计512MiB；RPC及远端会话保持有界超时和64KiB分块。

新增离线用例真实执行合成目录内的部分文件替换故障及完整原件恢复；注入Popen执行断连、同token重连和恢复验收；直接运行远端Worker验证外来token/新发布/不同版本/错误清单拒绝。Unix flock/systemd/生产TLS由拒绝联网fixture替代，Windows ACL对本地合成目录实际核验。未修改旧run、stderr白名单、编译hook、task卡或Delivery。

新run `20261014-owner-work-ssh-review-01` 将独立执行部署Python全套、PowerShell门禁、273项Node、所有现有局部类型与完整PC类型、原完整post-task-compile-hook。依赖使用缓存离线安装，Maven采用离线参数；新回执与日志SHA以AiCore run `fresh-checks/verification.json`为准，只有原candidate evidence verifier、Runtime.integrate、Finalizer及Hermes verified receipt成立后才算正式completed。下文测试表仅为旧候选历史，不替代新回执。生产SSH/SCP/systemctl、部署、生产库访问及真实资金操作全部NOT_RUN。

本任务只实现源码并离线验证。实际 SSH/SCP/systemctl、生产停服、生产恢复、生产 SQL、真实财务写入均 **NOT_RUN**。没有推送；源码、测试与本报告由一个本地结果提交交付，最终提交号见 Git/controller result record。Owner 的工程授权不等于本报告授权上线。

## 输入与范围

输入 HEAD/控制器基线为 `a288665624da368db5f9ee89dd04c40576840d22`。先读取 inbox 授权、AGENTS.md、AGENT.MD、DOCUMENT_INDEX、CURRENT_DEVELOPMENT_STATE，保留 A 的事务核心、原 Python 回归和五类 PowerShell 门禁。所有手工项目编辑均在 `scripts/deploy/**` 与本报告内；未修改 inbox、compile hook、控制器树、类型规则或产品代码。

只读核对 AiCore `reports/work-rescue/20261014-wealthhub-release-readiness/systemd-contract.txt` 与 `application-backup-manifest.json`：74 个文件，旧 JAR SHA `0f54b41430c150004d7c71a2667898d204b513b535c759bd5be25e0f435e39bf`，unit/override SHA 与 A 契约一致，真实 NGINX 配置为 `/www/server/panel/vhost/nginx/java_wealth-hub-1.conf`。没有读取或复制生产 db_config.json，也没有读取真实应用备份内容或凭据。既有 manifest 的 ACL 描述不能替代磁盘权限检查。

## 可调用入口与代码证据

| 实现 | 文件与起始行 | 行为 |
| --- | --- | --- |
| 显式 Work 入口 | `scripts/deploy/standard_deploy.ps1:1` | 固定目标；缺少完整 readiness/artifacts/backup receipt 即在网络前拒绝；齐备后调用 Python transport，旧默认调用仍保持阻断 |
| 完整 Python CLI | `scripts/deploy/ssh_stream_adapter.py:226` | `--readiness --jar --pc --h5 --backup-parent --backup-receipt`；本任务未运行真实 CLI |
| 发布证据及制品绑定 | `ssh_stream_adapter.py:44`、`:59` | target=baota-124；owner_approved=true；expires_at 为未过期 Unix 秒；build/types/offline_recovery/schema/roles/backup/release_approval 全部 PASS；artifacts 必须等于实际本地完整清单；originals 必须等于远端完整元数据清单 |
| 受限本地目录 | `ssh_stream_adapter.py:25`、`verify_backup_acl.ps1:1`、`systemd_local_rollback.py:106` | 拒绝链接/junction；Unix 要求当前 uid 且无 group/other 权限；Windows 使用真实 .NET ACL，仅当前 SID/SYSTEM/Administrators 可获 Allow，owner 亦受限；对子目录、文件、manifest 再验 |
| 有界持续会话 | `ssh_stream_adapter.py:93`、`:114`、`ssh_stream_remote.py:170`、`:329` | 一次 SSH Popen，BatchMode=yes、StrictHostKeyChecking=yes、ConnectTimeout=10；同一远端进程持有非阻塞 flock 直到 EOF；RPC 默认 45s，远端总会话 900s；仅结束自有 SSH 子进程，不清理其他进程 |
| 固定路径与完整原件 | `ssh_stream_remote.py:42`、`:52`、`:70` | JAR、完整 PC/H5、unit/override/指定 NGINX；未知 dist 对象、遍历、symlink 拒绝；无 DB 凭据路径；长度、SHA、mode、uid、gid 清单 |
| 服务契约与身份 | `ssh_stream_remote.py:92`、`:116`、`systemd_local_rollback.py:53` | 保留 simple/www/固定 WorkingDirectory/control-group/on-failure、Xms128M/Xmx512M；解析 systemd 实际 ExecStart；核验 MainPID、所有固定 JAR 实例、进程 www uid 与有效 argv |
| 真实探针实现 | `ssh_stream_remote.py:128`、`:145` | 固定 loopback:8766 JSON UP；NGINX HTTPS loopback:443、真实域名 SNI/Host、默认 CA 校验、`/api/v2/mobile/overview`=401；PC 首页 200 为独立条件；无代理/重定向绕过 |
| 流切换与恢复 | `ssh_stream_adapter.py:182`、`:198`、`:211`、`ssh_stream_remote.py:170` | 分块不超过 64KiB，严格 offset/length/SHA，fsync；上传前及写前再次核验当前原件清单，上传期间漂移硬阻断；同文件系统 os.replace 每个 JAR/资产；删除旧资产；恢复原 mode/uid/gid、空目录与完整文件清单；没有服务器完整备份 |
| 事务及失败语义 | `systemd_local_rollback.py:149` | 本地原件 SHA/清单/ACL、远端二次漂移检查与确认 receipt 全部完成后才 stop；任一停服后失败流式恢复并验收；断连/恢复失败明确 ROLLBACK_FAILED_MANUAL_RECOVERY_REQUIRED；恢复未确认时删除 rollback_backend，关闭会话失败不能冒充成功 |

真实 `readiness` 是 Work 独立提供的 JSON 对象，字段只允许 `target/expires_at/owner_approved/checks/artifacts/originals`。`artifacts` 使用 release_inventory 的 kind/size/sha256，`originals` 使用 inventory 的 kind/mode/uid/gid/size/sha256，包括目录、`config/unit`、`config/override`、`config/nginx`；Work 必须把独立核验的当前文件证据及目录元数据映射为该清单。旧 74 文件 archive manifest 本身没有全部目录元数据，不能直接冒充此完整 readiness。backup-receipt 是排序紧凑 JSON manifest 的 SHA256，必须独立确认；OFFLINE_AUTO_CONFIRM 在 live transport/CLI 被拒绝。测试中的合成 PASS 对象只交给注入 Popen，没有具备真实放行证据。

## 实际验证

每次命令均通过单个原生命令调用，完整 raw output 由控制器 JSONL 保存；未重跑任何 baseline 或覆盖源码比较。

| 命令 | 最终退出码/结果 |
| --- | --- |
| `python -m unittest discover -s scripts/deploy -p test_*.py -v` | 0；64 tests / OK（包含原测试被既有 discover 导入重复执行，不能解释为 64 个独立新增用例） |
| `powershell -ExecutionPolicy Bypass -File scripts/deploy/test_standard_deploy_offline.ps1` | 0；五类 fixed-target/live-gate 门禁 PASS |
| `python scripts/deploy/compare_controller_types.py --report <指定 controller-baseline/baseline.json> --log <指定 types-baseline.log>` | 0；NO_NEW_DIAGNOSTICS，baseline=0/candidate=0，added/removed 均空 |
| `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1` | 0；完整 backend package + frontend build，Build passed；最终文档写入后再执行同一完整 hook |
| `npx.cmd --yes --package @playwright/cli@0.1.22 playwright-cli -s=mydca-ssh-stream-20261014 open about:blank --browser msedge` | 0；具名隔离 Edge 会话 |
| 在 web/pc-app 执行 `npx.cmd --yes --package @playwright/cli@0.1.22 playwright-cli -s=mydca-ssh-stream-20261014 run-code --filename tests/typecheckClearance.browser.js` | 最终 0；原 fixture 完整回显，未编辑 fixture |

新增 transport suite 有 10 个 test 方法，覆盖拒绝联网的 Popen stub 成功、健康 DOWN/HTML200/upstream 非401/启动失败/上传断流回滚、不可恢复断连、RPC timeout/截断、锁竞争、cold start、契约漂移、双实例、异常服务、备份读断流、配置清单/元数据漂移、缺 readiness、ACL 前置拒绝；直接执行远端 Worker 的实际临时文件写入/seal/commit/verify/restore、旧资产删除、空目录/元数据恢复、固定 health/upstream 分派、flock 生命周期及错误 root 身份拒绝。真实 Unix flock/systemd/proc/TLS 握手用 stub，仅其代码及分派离线验证；Windows ACL 是对任务目录内合成文件夹实际设置与检查，拒绝 Everyone 后仅允许当前 SID。

中途失败如实记录：初版 ACL 测试发现 PowerShell Security 模块加载失败，已改为 .NET ACL 且任何检查错误退出即阻断，实测最终通过。浏览器首试因本地静态服务器未启动而 ERR_CONNECTION_REFUSED；依赖安装完成后启动仅 127.0.0.1:3110 的 Vite，原 fixture 重跑通过。没有后台 API 服务或生产连接；进程收尾交由控制器。未修改 hook 的静默行为。

最终浏览器 raw result：

```json
{"result":"PASS","boundary":"ALL_BUSINESS_REQUESTS_INTERCEPTED_NO_SERVER_OR_PRODUCTION_CONNECTION","widths":[1440,1100,760],"pageerrors":[],"unexpectedRequests":[],"interceptedRequests":29,"financialWrites":0,"fixtureWrites":1}
```

## 类型证据与现场边界

只使用控制器指定报告：base `a288665624da368db5f9ee89dd04c40576840d22`，baseline report SHA `7ab6f4773349fcaa48a5455e21fc6b53fb397030744094207b0d26334737051f`，baseline/final 空日志 SHA `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`。实际控制器基线为 0，历史 44 条不适用于本轮；证据见 `scripts/deploy/types-comparison.json` 与 `types-final.log`。没有触碰或检查 isolated baseline tree。

真实部署仍需 Work 在独立授权下提供完整 readiness、当前 originals/receipt，验证实际 SSH 信任/权限、远端 Python3、系统 CA、flock、NGINX HTTPS/路由与冷启动时间。前端以单文件原子替换实现，整套资产不是一个原子目录快照；服务停服期间切换，任何中途失败由本地原件恢复。实际 TLS、远端锁竞争、生产停服/恢复和现场验收均 NOT_RUN，本报告不能替代现场演练，也不能由离线 fixture 放行上线。
