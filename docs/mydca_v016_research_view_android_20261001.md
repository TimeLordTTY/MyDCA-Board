# v0.16 Android 研究方案只读查看

Android 底部「研究」进入研究方案列表，保留「查看最近成功回测」入口。研究结果不代表未来表现，不构成投资建议。

## 查看范围

- 列表每页读取 20 条方案，只展示 DRAFT / ACTIVE；若本页只有归档方案，明确本页空态并允许继续翻页。
- 卡片展示 strategy/version、服务端证据警告、已读取历史中的最近关联运行，以及成功运行的收益、年化与最大回撤。未知值显示「未知」，失败运行不展示成功指标。
- 详情重新读取方案，展示来源候选、来源 run、创建时 canonical 参数快照、独立参数草稿、dataset hashes、证据 hash 引用与来源 engine 证据。
- 方案运行历史用 `researchPlanId` 关联，与创建时 `sourceRunIds` 证据区分。失败运行展示状态与 failureCode。
- 历史初始读取当前 owner 最近 50 条（包括其他方案），手动「读取更早回测历史」追加。未在已读取窗口找到运行不等于从未运行；界面明确这一限制。无自动轮询。
- 登录失效复用认证拦截器退出会话；403、404、网络/服务错误明确显示。新研究页面请求失败清除相应旧数据，刷新重新从最近历史开始，不将读取故障当成空数据或有效证据。

## 只读边界

仅新增 GET `/api/v2/research-plans?page&size`、GET `/api/v2/research-plans/{id}`、GET `/api/v2/backtest-lab/runs?page&size`。Android 不声明研究创建、编辑、运行、compare/evidence POST 方法；不生成订单、交易或财务记录，不申请新权限。页面数据只在内存保留，离开页面后重新读取；详情返回支持系统返回键。

后端研究 migration 仍需部署方人工部署，本任务未连接任何数据库。Android 发布版本保持当前 0.15.0 / 16；本轮是 v0.16 功能增量，版本发布由后续收口安排。本任务只本地提交，无推送或 CI APK 交付。

## 验证

新增 MockWebServer 单元测试覆盖后端 JSON/参数与证据映射、失败 run 与 nullable metrics、方案关联隔离、GET 方法/路径/空请求体/认证头、401 会话失效、403/404/503、Compose 使用的加载/空态/错误/重试状态，以及 DRAFT/ACTIVE 过滤。

真实设备 Compose 渲染、登录权限及断网交互仍需人工体验验收；单元测试验证的是 Compose 消费的状态与网络边界。

2026-10-01 本地 `gradlew.bat testDebugUnitTest assembleDebug lintDebug --no-daemon` 全部通过：41 个测试类、281 项测试，0 失败/错误；新增研究测试 4 项。lint 为 0 error / 2 条既有 warning。`post-task-compile-hook.ps1` 后端 package 与前端 build 通过，`git diff --check` 通过。PowerShell 直接启动 hook 遇到环境变量进程启动错误，改由 cmd 执行同一脚本；未修改 hook。未推送，未生成 CI 制品。
