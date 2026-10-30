# Android免费本地CI定义

Owner授权：[统一CI Hub最终结案任务](https://github.com/TimeLordTTY/ai-core/issues/90#issuecomment-6513982294)。原Android工作流逐字节移入本目录，保留原`push`到`v2`及`workflow_dispatch`语义，不凭空添加PR触发。

正式AiCore CI Hub保留JDK17、原Gradle Wrapper、testDebugUnitTest、assembleDebug、lintDebug全部检查；全部通过后才收集本次Debug APK并核完整source SHA、工具版本、构建变体、内嵌版本/debuggable和APK SHA256。使用原隔离Linux/公开SDK与缓存，不接正式数据库、签Release或上传正式发布包。

本迁移需在Hub已正式支持并验收`.ci/workflows`同字节来源后合并。GitHub只从`.github/workflows`自动启动云端Runner；移出该定义后不再使用云端执行。历史failure不改写，付款、保护、可见性和生产不改变。

低权限本机Watch观察`v2`真实push后自动入队；显式手工构建使用同一正式控制器：

```text
python -B tools/ci_hub/hub.py auto --config <正式私人控制器配置> submit-branch --repo mydca
```

PR没有原触发时必须列NOT_APPLICABLE，不把空计划当PASS。普通文档/CI迁移不会因此自动编译后台或前端；本轮Owner明确要求的Android完整跨平台验收单独保存真实结果。

每次完成独立只读evidence后先持久化/读回脱敏文本审计，再自动回收已退出、无引用的历史VM/构建产物，保留提交、命令、结果、错误、哈希及回执。回收后明确原始证据不可重新复验；新版本重新实跑。当前验收所需原件、活动/UNKNOWN、项目源、私人/财务/生产数据均不删除。

自动CI不审批或合并PR，不启动生产或正式财务操作。原合法作者负责完整diff、自查、最新Review、实际平台要求、普通预期head合并和远端字节验收。回滚经正常PR将同字节定义迁回原路径，不能同时保留双份歧义定义。
