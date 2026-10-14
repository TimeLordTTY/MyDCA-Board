# systemd 与本地流式回滚离线整改（2026-10-14）

**结论：BLOCKED；离线检查通过不等于任务全部验收或生产可上线。**

任务 `task-mydca-systemd-local-rollback-20261014`，输入源码
`9008ec9e646b3804fd7d6a6fd6e0651be17338e1`。只修改 `scripts/deploy/**`
与本文，不修改 inbox、控制器基线、hook、systemd、NGINX、SQL、业务源码或财务记录。
没有生产连接、部署、停服、交易、真实登录、生产数据库访问或 push。
只创建一个本地候选提交；最终 result SHA 从本候选 `git rev-parse HEAD` 独立获取，
由控制器/Finalizer 后续审核，本文不自称已交付或 owner 已验收。

## 实装与明确限制

旧部署入口会 TERM 所有匹配进程、nohup 以 Xmx1024M/Xms256M 启动、在服务器
创建完整大备份，与指定 systemd 契约冲突。两个入口现已在网络/服务/构建动作前
阻断；不保留危险 fallback。**现有生产部署命令将失败，不能把该候选直接当成
替代部署工具。真实 SSH 适配器没有实现，任务的完整部署替换目标尚未完成。**

新增离线事务使用真实沙盒文件、64KiB 有界原字节读取/写入、文件 SHA256 +
manifest SHA256 + 确认 receipt。备份包括 JAR、全部 PC/H5（含资产和空目录）、
明确列出的合成配置；记录 mode/uid/gid。源清单备份后与确认后均重核。
SHA/大小不匹配、源漂移、备份缺失、确认缺失在切换前拒绝。正常切换不改配置，
恢复只从本地原件读取，不在模拟服务器生成完整旧包备份。

确认函数接收本地目录与 manifest receipt，必须返还相同 receipt；离线自动确认
只能用于 offline adapter。SHA 链不是第三方数字签名或跨重启恢复凭证。
本轮只验证同一进程内失败恢复，不宣称进程崩溃后现场恢复接口可用。

systemd 契约逐字段拒绝未知/漂移，包括 Type、PIDFile、ExecStart、User/Group、
WorkingDirectory、KillMode、Restart/超时、unit/drop-in 路径及两份 SHA。
只接受报告的 Java 与有效 Xms128M/Xmx512M。MainPID 必须正整数且唯一实例
与有效 argv 一致。部署成功还要求严格 JSON UP、独立 upstream 未认证 401、PC200；
HTML200、DOWN、upstream HTML200 不通过。上述系统/HTTP 状态均为合成 fixture，
没有调用真实 systemctl/curl/ssh/scp。退出码 0=模拟部署通过，1=切换前阻断或已验证
模拟恢复，2=失败/无法确认恢复。失联绝不输出恢复 UP。

冷启动仅接受明确 inactive/零 PID/零实例；failed/unknown 不按冷启动处理。
冷启动失败恢复原字节和原 inactive，标记 ORIGINAL_INACTIVE，不声称后端 UP。
沙盒用真实 O_EXCL 锁竞争覆盖整个事务，未实现/验证现场持续 flock 会话。
Windows 能验证原字节与权限位清单，不证明 Linux www 属主或 NTFS 访问控制。

## 阻断证据与未实现项

事实源只读读取：
`D:/Project/ai-core-runtime/reports/work-rescue/20261014-wealthhub-release-readiness/systemd-contract.txt`
及同目录 `sample-0.txt`。unit SHA 为
`8cb49e86773d46e02e4ef9c87898a81206f67efa16d91cdbb0989704f74e54d7`；
override SHA 为 `a1be5e19c472cab60ac1b7292450257de6dccfa81aa012a9c5f4584b9a56aef4`。
样本是既有现场历史证据，本轮没有重新核验 MainPID=2374479 或历史 UP。

| 门禁 | 当前结果 |
| --- | --- |
| 必要配置完整绝对路径清单 | BLOCKED：契约只列 db_config.json 文件名，不能猜测位置/完整性 |
| 独立 NGINX upstream URL | BLOCKED：sample 只有裸 401，不能反推探针路径 |
| 受限 D:/AicoreRescue Git 外备份 | NOT_IMPLEMENTED：只执行允许目录内合成沙盒，未创建该路径或验证 ACL |
| 真实 SSH 读取/上传流与异常协议 | NOT_IMPLEMENTED：只有文件适配器；真实入口禁止执行 |
| 持续 flock、真实 Type/PID/Restart 闭环 | NOT_IMPLEMENTED/NOT_RUN：O_EXCL 沙盒不能替代 |
| Unix mode/www owner 恢复 | NOT_RUN：记录清单并验证 Windows 沙盒相等；未做 Linux 权限演练 |
| 现场恢复与冷启动、断连后状态 | NOT_RUN：本轮没有服务器操作授权 |
| 峰值 RAM/磁盘、传输耗时、重启等待 | NOT_RUN：没有现场量测；不引用旧测试证明峰值安全 |

以上缺口没有被 mock、源码存在或 hook 通过改写成已验收。必须在后续获授权阶段
补充事实清单及完成真实适配器/权限/锁协议，再做独立隔离/现场恢复评审。
本轮不创建后续业务任务、不改变控制面或调度。

## 验证记录

所有工具调用只含一个原生命令；完整原始输出由控制器 JSONL 保存。
没有 git show 覆盖、checkout/reset/restore、临时替换源文件，未写控制器隔离树。
测试 fixture/备份仅在 `scripts/deploy` 内生成并由测试释放，不杀任意进程。

| 命令/验证 | 真实结果 |
| --- | --- |
| Python 原部署回归入口 `test_deploy_offline.py` | exit 0，16 tests |
| 新离线回归 `test_systemd_local_rollback.py` | exit 0，13 tests（也包含在上述入口，不重复计作独立覆盖） |
| PowerShell 原部署回归入口 | exit 0，5 个固定目标/阻断案例，无网络/服务/构建动作 |
| 既有 `test_mysql_isolated_rehearsal.py` | exit 0，25 tests；既有模拟，无真实数据库/容器 |
| 完整未改动 hook | 首次 exit 0，Build passed；最终文档后复跑见下方 |
| 类型检查首次尝试 | 依赖尚未安装，MODULE_NOT_FOUND，工具 exit 1；没有把此失败当对照结果 |
| 依赖安装后候选类型检查 | 44 条诊断；直接 PowerShell 工具调用报告 exit 1，原生 Node 退出码以对照 JSON 为准 |
| Edge 原样 foundation fixture | exit 0；PASS，18 cases，3 个实际拦截业务请求 |

现有 Python/PowerShell 测试入口随旧实现变更：健康探针原语料保留；旧上传成功、
nohup 与服务器备份测试不再适用，由实际 Bash 阻断与流式事务测试代替。
这是部署行为的变更，不宣称旧部署回归语义全部兼容。

事务覆盖成功、HTML200、DOWN、启动失败恢复、独立 upstream 失败恢复、损坏/缺失
备份和 manifest、读流损坏/断连、恢复断连、服务异常、冷启动成功/失败、双实例、
锁竞争、确认缺失/receipt 校验、源在确认时漂移。真实复制/流写/哈希/全量资产恢复
运行在沙盒；socket/create_connection/Popen/os.system 被拒绝桩保护。

## 浏览器原始证据

没有新建/修改任何 `web/pc-app/tests` 文件。复用原样 `tests/foundation.browser.js`，
使用 Playwright 技能指定的实际浏览器执行方式；本地 Vite 仅提供静态模块。
具名会话 `mydca-systemd-rollback-20261014`，浏览器 msedge，从 about:blank 开始。
命令在 `web/pc-app` 原生执行：

```text
npx.cmd --yes --package @playwright/cli@0.1.22 playwright-cli -s=mydca-systemd-rollback-20261014 open about:blank --browser msedge
npx.cmd --yes --package @playwright/cli@0.1.22 playwright-cli -s=mydca-systemd-rollback-20261014 run-code --filename tests/foundation.browser.js
```

最终原始输出回显了完整原样 fixture。返回 result=PASS，
boundary=ALL_BUSINESS_REQUESTS_INTERCEPTED_NO_SERVER_OR_PRODUCTION_CONNECTION，
pageerrors=[]，unexpectedRequests=[]，financialWrites=0，interceptedRequests=3。
所有业务 HTTP 用 fixture fulfil，外域 abort，不使用真实凭据。
该前端检查不能替代 Java/systemd/NGINX 现场验收。
Vite/Edge 会话的清理归控制器，本轮没有自定义进程清理脚本或杀进程。

## 控制器唯一基线与候选比较

只读取指定控制器报告及日志：
`D:/Project/ai-core-runtime/agent-runtime/dispatch/dual-worker-state/runs/20261014-031328-16688-1/controller-baseline/baseline.json`
与同目录 `types-baseline.log`。基线 status=passed，type_exit_code=2，44 条诊断；
未重新运行基线、未编辑该树。候选比较脚本只运行当前树 Node 类型检查，先校验
控制器日志 SHA 与报告诊断清单，再比较归一化诊断 Counter 和原生退出码。
证据见 `scripts/deploy/types-comparison.json` 与 `types-final.log`。
这只能说明没有新增历史类型诊断，**不能称全量类型检查通过**。

最终比较脚本 exit 0：baseline/candidate 原生退出码均为 2，诊断均为 44 条，
added={}、removed={}，完整候选原始日志与控制器基线日志 SHA256 完全一致：
`da3078b9267dc499733e483a4e702346229d891167fa4e019245010924adce32`。
控制器 baseline.json SHA256：
`d863b19aae973010c353fd81cd69099048cbf35279e8f9311a3d9de0c7ddf823`。

hook 原文件/提示逻辑未修改，按本轮任务明确要求原样运行。实际文件成功只 stdout
`Build passed.`，没有为了满足文字中的提示要求添加 Beep/Toast。
hook Maven 使用 -DskipTests，因此不把 hook 的编译通过写成 Java 单元测试通过。

## 测试源码与证据 SHA256

下表为最终被测试工作树原字节的 SHA256；源码均随唯一候选提交。
`scripts/deploy/.gitattributes` 保持 Bash LF，并禁止原始 types-final.log 的 Git
文本换行转换，保证原始类型日志可以从候选独立复核。

| 文件 | SHA256 |
| --- | --- |
| scripts/deploy/systemd_local_rollback.py | 3c764b9680a6aa00f72c2a277dae49e8cfa5266fc36fff68ac45ec40a136c512 |
| scripts/deploy/standard_deploy.ps1 | 73226c242750113bcbb31e8e34c587c35ac68614e77317d7d9691decdb9af875 |
| scripts/deploy/remote_deploy.sh | 6904c9a4b8b469c0600286018af5267cd4ef44de512c7a5d4de7241afff9d169 |
| scripts/deploy/test_deploy_offline.py | d7d3c02d8c9dae6f78338107b2d7c09ba57e4700528ea2d3fe63f2be99df0b6f |
| scripts/deploy/test_systemd_local_rollback.py | fa84f560eee30a504fd05aa5a48e841299c0541b02ced1bf84e13256e447560e |
| scripts/deploy/test_standard_deploy_offline.ps1 | 69f705574b7cbd920096e37c73b1b4147cb5064266e768324cde0ac9895323ec |
| scripts/deploy/compare_controller_types.py | 20eb1215ddf29c14212844f4bcb2e245232f489292e86437d3d6d97bf7e34060 |
| scripts/deploy/README.md | 804114250c8994be04642f72a36118473417f3b7ffe4ed3922a6eaf7c75eb263 |
| scripts/deploy/types-comparison.json | 68fe30892c63575925fdfc1075311c7f39b9ef4bf3d797da99fbb1fa34777c29 |
| scripts/deploy/types-final.log | da3078b9267dc499733e483a4e702346229d891167fa4e019245010924adce32 |
| 未修改 scripts/post-task-compile-hook.ps1 | 7ade19f61a51870927dbb8cbf7627e087403123fbcfc6017977b980acf186c99 |
| 未修改 web/pc-app/tests/foundation.browser.js | 3078c5de87af923512e842f0c03b5dbf0f4cd08c347a3d00683bf9c5e0334f8c |

fixture 与 hook 的工作树 Git blob 分别为
`89ac50b0c88570b53d90a11ec4dab0f2245f526f`、`06db161cfbc2535ae2046ac4fae1e3313a1da285`，
与输入 HEAD 两对象一致。本文最后编辑后必须再运行完整原 hook；最终退出码/日志
以控制器该次原始输出为准，失败不得提交“完成”声明。
