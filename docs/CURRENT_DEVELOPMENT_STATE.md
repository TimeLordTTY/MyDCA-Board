# 财富中枢当前开发状态

- **作者**：ChatGPT（依据 v2 代码、AiCore Delivery Manifest 与 GitHub Actions 证据同步）
- **更新时间**：2026-09-27 23:40 +08:00
- **工程仓库**：`TimeLordTTY/MyDCA-Board@v2`

> 本文件是“当前实现状态”的首要事实源。长篇设计文档、版本专项报告和 Phase1/Phase2 历史总结保留设计/历史价值；若其中的“当前状态、下一步、尚未实现”与本文件冲突，以本文件和实际代码为准。

## 当前阶段

财富中枢已不处于项目初始化阶段。当前主线为 **Phase3：原生 Android + 草稿式安全记账闭环**，并持续保持 PC/Web、Java 后端、MySQL、Python 工具能力。

当前 Android 应用版本：
- `versionName = 0.8.0`
- `versionCode = 9`

“v0.8 草稿强幂等”是后端可靠性里程碑；Android `0.8.0` 是外部分享快速采集版本，二者版本号相同但属于不同层，互不依赖即可独立发布。

## 已完成的 Phase3 主能力

- `draft_ledger_entry` 草稿表、列表/详情/编辑/preview/ignore/confirm API。
- 文本记账 `parse-text → draft-from-intent`；解析只生成候选和 DRAFT。
- Android 真实登录与 Keystore/AES-GCM Token 安全存储。
- 总览、今日待办、草稿箱、资产、设置。
- 手工文本、Photo Picker + 本地 ML Kit 中文 OCR、支付通知候选。
- v0.6 加密 Draft Outbox：可恢复网络/5xx 失败只重试“创建 DRAFT”，401/403 暂停，业务 4xx 不循环。
- v0.7 全局“记一笔”快速采集中心：手工、OCR、支付通知候选、Outbox 四入口统一导航。
- v0.8.0 外部分享快速采集：系统 Share Sheet 的文本 / 单张图片只预填到现有人工采集流程，不自动 parse/OCR/draft/preview/confirm。

## v0.8 强幂等（已完成）

任务：`task-mydca-v08-draft-strong-idempotency-20260927`  
结果提交：`ea8b3618e25c648127c62750c307ae8af976dd56`

- 应用层 `sourceType + sourceRef` 幂等查询继续保留。
- 新增 `sql/updatesql/20260927/` 三步 migration：重复来源预检、空来源归一化、user/family scope 唯一键。
- `source_ref IS NULL` 保持非幂等旧语义。
- 仅捕获明确 `DuplicateKeyException`；冲突后按同一可见作用域重查既有草稿，其他数据库异常不伪装为成功。
- 后端 70 项测试通过；Android 24 个测试类 / 119 项通过。
- AiCore Delivery Manifest：`completed / verified_wecom_receipt`。

**重要**：migration 尚未由本次自动任务连接或执行到任何数据库。上线前必须先在目标环境执行只读重复数据预检并人工确认；生产数据库迁移不属于普通后台自动任务。

## Android v0.8.0 外部分享快速采集（已完成）

- 任务：`task-mydca-android-v080-external-share-capture-20260927`
- 详细说明：`docs/mydca_android_v080_external_share_capture_20260927.md`

- `ACTION_SEND` 单条 `text/*` 或 `image/*` 进入既有手工文本 / 图片 OCR 采集页，**只预填**。
- 分享文本与图片 URI 只在进程内保留，不落盘、不写日志、不上传；同一 payload 只消费一次，未登录期间保留到登录后消费一次。
- 分享图片不自动 OCR，必须用户点击“使用此图片并识别”；识别后仍需用户手动解析、手动生成 DRAFT。
- sourceRef：`android-share-text-<uuid>` / `android-share-image-<uuid>`，不含原文 / 文件名 / URI，Outbox 重试复用同一值。
- 返回 / 取消、切换底部导航、成功生成 DRAFT 都会清空一次性 share target。
- 未新增任何广泛权限；`ACTION_SEND_MULTIPLE` 明确不支持。
- Android 28 个测试类 / 156 项通过、`assembleDebug`、`lintDebug`（0 error / 2 条既有 warning）、`scripts/post-task-compile-hook.ps1` 全部通过。

## Android CI APK 真实证据

### v0.8.0
- 状态：`NOT_PRODUCED`（本次执行进程只做本地提交、不推送，工作流未被触发）。
- 待真实推送 `v2` 触发 `Android test APK` 后回填 Run ID / Artifact ID / APK 文件名 / CI APK SHA-256。
- 不得用本地 APK 哈希冒充 CI artifact。

### v0.7.0
- source commit：`6098a9728f23dc6e0b6bbd5b7d0460c5630f4252`
- GitHub Actions：`Android test APK`
- Run ID：`36320197608`
- 结论：`success`
- Artifact ID：`10931548073`
- Artifact：`mydca-android-v0.7.0-6098a9728f23dc6e0b6bbd5b7d0460c5630f4252`
- APK：`MyDCA-Board-v0.7.0-6098a972.apk`
- CI APK SHA-256：`D18D0CC67F7428495E6A6F2B0ED50100D556301368D6853FD0489AD2325E3B2B`

### v0.6.0
- source commit：`a732c3bc569959a4f53a6448d8c7e6ce3dcc63ee`
- Run ID：`36306899948`
- Artifact ID：`10928005959`
- APK：`MyDCA-Board-v0.6.0-a732c3bc.apk`
- CI APK SHA-256：`2298E56D4B9DF18218CAD17A1CCFA3EA094592364F2AFA2FD5E5582B103BB0CD`

旧文档中“v0.6/v0.7 CI 制品 NOT_PRODUCED / 待回填”的表述已经过期；v0.8.0 的 CI 证据仍以真实工作流结果为准。

## 自动开发与交付

普通工程当前通过 AiCore：
`ChatGPT/owner → ai-core Git task → Windows scheduler → approved Codex executor → MyDCA v2 → tests/build → commit/push → finalizer → Hermes → verified WeCom receipt`

`scripts/post-task-compile-hook.ps1` 构建成功后必须静默：只输出 stdout/log，不播放声音，不弹 Windows Toast/Popup。

## 当前安全边界

- 不自动交易。
- AI/通知/OCR/Outbox 不自动正式入账。
- 不把 preview 当 confirm。
- 不绕过 `DRAFT + fresh preview + confirmSupported + 用户二次确认`。
- 不把 Token、密码、Cookie、完整通知原文、图片 URI 写入普通日志或明文存储。
- 普通后台工程不连接生产数据库、不修改真实财务记录。
- migration 存在于 Git 不等于已部署。

## 当前真正未完成

1. **真实设备体验验收**：系统 Share Sheet 文本 / 单图、Photo Picker、支付截图 OCR、不同厂商 Content URI 与通知监听授权 / 候选体验仍需真机人工验收。
2. **v0.8.0 CI 制品证据**：本地提交尚未推送，`Android test APK` 的 Run ID / Artifact ID / APK 文件名 / SHA-256 待真实工作流产出后回填。
3. **数据库 migration 上线**：v0.8 唯一键脚本尚未部署；生产执行前必须先跑重复数据预检。
4. **长期能力**：投资订单类草稿确认、完整结算/持仓影响、策略建议与回测闭环继续按设计推进。

## 下一工程任务

下一项普通、可自动化的业务任务：**Android v0.8.0 真实设备验收与 CI 制品回填**（同一 Android 版本线，不改账本语义），或按 owner 决策进入 v0.9 采集入口扩展（桌面小组件 / 通知栏快捷入口）。两者都不需要生产凭据或真实账本写入。