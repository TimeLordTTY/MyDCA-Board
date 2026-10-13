# 财富中枢生产版本只读核验（2026-10-13）

- 任务：`task-mydca-production-readonly-version-check-20261013`；仅报告交付，不是生产发布批准。
- 新鲜检查时间：2026-10-13 19:12:52.530–19:12:55.001，Asia/Hong_Kong（UTC+08:00）；原始回执时间为 11:12:52.530–11:12:55.001 UTC。
- 输入 HEAD：`951b01c07d753fd55baaf812de15264072c81945`。本地提交存在不证明生产部署。

## 结论

**当前公开 PC 首页字节与 10 月 10 日部署记录 MATCH；完整生产版本 UNKNOWN，当前运行 JAR NOT_VERIFIED。** 没有证据证明生产包含年轻版最终提交 `951b01c07d753fd55baaf812de15264072c81945`，也不能仅凭首页相同确认整个应用仍为 10 月 10 日部署版。HTTP 200、资源名称和修改时间不代表上线安全验收通过。

## 方法及新鲜公开回执

使用 Node.js 原生 HTTPS GET、默认 TLS 证书验证及 `Accept-Encoding: identity`。仅在内存下载首页和其直接引用的同源、`/wealth-hub/` 下 JS/CSS，计算原始响应字节 SHA-256。不执行网页 JavaScript，不登录、不提交表单、不请求业务 API 或登录端点。客户端不跟随重定向；三次响应均 200。命令退出 0；完整原始输出由控制器 JSONL 保存。历史 rescue archive 的报告只用于审阅，未采用其旧测试回执作为本次成功证据，未修改 archive。

以下目标同源为 `https://www.timelordtty.cn`：

| 目标 | HTTP / Content-Type | bytes | SHA-256 |
| --- | --- | --- | --- |
| `/wealth-hub/` | 200 / text/html | 600 | `a7546fac99de8c0144dda9bebca044f7803e6ad22b2d6a078da99e73caeb4e20` |
| `/wealth-hub/assets/index-CaU5zZtk.js` | 200 / application/javascript | 1163613 | `1a5f24e933d51c2cdff845623f401d5897680588129d388e06420a01156a2f81` |
| `/wealth-hub/assets/index-u3YwI7p5.css` | 200 / text/css | 358922 | `4a806ce57e86e1f5c0802ad8b4468d9ea0ebf88623749d6972a67dbaa13055fe` |

三项 Last-Modified 均为 `Sat, 10 Oct 2026 02:45:54 GMT`。ETag 依次为 `"6ac9a6e2-258"`、`"6ac9a6e2-11c15d"`、`"6ac9a6e2-57a0a"`；三项均未提供 Cache-Control、Content-Encoding 或 Location。ETag 不等于 SHA-256 或 commit。未获得独立缓存层/源站证明。首页未发现 40 位十六进制提交元数据，此检查不能证明 JS 或服务器所有文件均无构建元数据。

## 历史对照与证据缺口

依据 [10 月 10 日生产部署报告](mydca_production_deployment_20261010.md) 的最终线上验收：

| 对象 | 历史证据 | 本次可证实范围 |
| --- | --- | --- |
| PC 首页 | SHA-256 `a7546fac99de8c0144dda9bebca044f7803e6ad22b2d6a078da99e73caeb4e20` | MATCH：当前公网返回字节相同 |
| PC JS/CSS | 历史报告称六项 PC/H5 资源与当时服务器文件逐项相等，未列逐文件 SHA | 本次两项哈希已记录；旧逐文件哈希缺失，比较 NOT_VERIFIED |
| 最终运行 JAR | SHA-256 `0f54b41430c150004d7c71a2667898d204b513b535c759bd5be25e0f435e39bf` | 当前哈希 NOT_VERIFIED，不能复用旧值作当前读数 |
| Java 进程与对应磁盘 JAR、服务器静态文件 hash/mtime | 仅当时报告 | NOT_VERIFIED，未 SSH |
| 当前服务 health | 仅历史 UP | NOT_RUN；首页 200 不证明后端 health |
| 当前生产 source commit/release | 未取得绑定当前制品的 manifest | UNKNOWN，不能归属于年轻版或其他精确提交 |

当前任务未提供 AiCore 现有 approved 只读 SSH executor 的配置、策略许可及可用性证明，当前工具亦无对应执行入口。因此 SSH NOT_VERIFIED；未调用本机 SSH、搜索凭据、新增 host trust、交互提权或运行高权限替代脚本。公网读取未被拒绝，不标记为 BLOCKED_READONLY_ACCESS；若既有审计门禁拒绝后续只读访问，应记录该状态并停止。

未取得本任务控制器 baseline 报告。源码/类型基线比较为 **BLOCKED_MISSING_BASELINE_EVIDENCE**，不自建或重跑基线、不修改隔离基线树、不使用 git show/checkout/reset/restore 或临时覆盖源码。任务明确指定的历史首页哈希对照独立于源码/类型基线比较。离线/隔离引擎 manifest 不作为当前生产发布证明。

## 发布前风险及未执行项

| 门禁 | 状态和未来所需证据 |
| --- | --- |
| AUTH-001 | BLOCKER 保留；[发布准备报告](wealthhub_pc_young_ui_release_review_20261010.md) 指出订单按状态/ID/取消/待结算的 owner 授权缺口。本任务不修复；跨用户/家庭真实隔离验证 NOT_RUN，需另行授权修复和验收 |
| 真实身份/JWT/Nginx | NOT_RUN；未来需真实身份、JWT 失效、角色边界及部署深链验收，静态 GET/mock 不证明权限有效 |
| 生产备份/恢复/回滚 | 当前备份 manifest 未核验，独立数据库恢复及当前版本回滚演练 NOT_RUN。历史备份和历史自动回滚记录不替代当前演练 |
| 精确版本链 | 需脱敏当前 release manifest 绑定完整 source SHA、构建 run、部署时间、PC 全资源清单与哈希、JAR SHA 和运行进程对应制品；服务器只读读数须来自既有 approved executor |

未连接生产数据库、读取财务记录、使用真实登录凭据、调用业务 API、执行交易/迁移/正式入账/部署、修改服务器配置文件或服务、创建业务任务、修改 inbox/审批/调度。真实财务写入 0。未推送。

## 工程验证边界

仅新增本报告；没有源码、测试或依赖清单变更。新鲜执行 `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0`，后端打包、前端依赖安装、前端编译完成，输出 `Build passed.`，退出 0；`git diff --check` 退出 0。禁用自动修复避免越出唯一 allowed_path；后端打包的 DskipTests 不代表后端单测通过。补记结果后再次对最终报告树运行同一 hook；最终命令回执为权威证据，旧 archive 的构建结果未复用。

本次未获准新增 fixture 或本地服务脚本路径，任务卡仅要求公开只读版本核验。隔离浏览器 fixture 验收 **NOT_RUN**；未伪造 fixture 回显、PASS、pageerrors、unexpectedRequests、interceptedRequests 或浏览器 boundary。公开静态下载不等于业务浏览器回归，用户要求的浏览器 PASS 回执尚未满足。缺少控制器基线，不宣称类型比较通过。工程提交不等于正式 Delivery、通知或生产发布完成。
