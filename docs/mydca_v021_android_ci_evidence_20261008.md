# v0.21 Android CI 调试包交付凭证

核验日期：2026-10-08（Asia/Hong_Kong）。任务：`task-mydca-v021-android-ci-apk-evidence-20261008`。

结论：已有真实成功构建，制品可下载，APK SHA-256 已按实际字节核验；真机安装待主人验收。本任务仅补充文档，不修改 Android/Gradle/workflow，不重复触发 CI，不推送、不发布正式版。

## 源码与构建关联

- 仓库：`TimeLordTTY/MyDCA-Board`，分支：`v2`。
- 核验时远端 HEAD、本地任务起点及 run/artifact 的 source commit 均为 `c7167576ac8ccb6c5b51d6baabc7b74cf1b48469`（`chore(release): harden v0.21 data and migration readiness`）。
- `gh api repos/TimeLordTTY/MyDCA-Board/branches/v2 --jq '.commit.sha'` 与 `git rev-parse HEAD` 实核一致；查询 workflow 的 v2 历史，最新 run 即本提交，未混用旧版本成功制品。
- 该提交的 `android-app/app/build.gradle.kts` 声明 `versionName = "0.21.0"`、`versionCode = 22`；下载 APK 经 Android SDK 35.0.0 `aapt dump badging` 实核为 `com.timelordtty.mydca`、`versionName='0.21.0'`、`versionCode='22'`、minSdk 26、targetSdk 35。
- 本次文档结果提交建立在上述源码提交之上，仅 README 与本凭证变化，Android 源码及 workflow 与实际构建提交一致。

## GitHub Actions 与制品

| 字段 | 实际查询结果 |
| --- | --- |
| Workflow name/path | `Android test APK` / `.github/workflows/android-test-apk.yml` |
| Run | [37724100264](https://github.com/TimeLordTTY/MyDCA-Board/actions/runs/37724100264)，attempt 1，event `push` |
| Status/conclusion | `completed` / `success` |
| Run 时间（UTC） | created `2026-10-08T03:43:50Z`；updated `2026-10-08T03:45:39Z` |
| Build job | `113138209216`，`build`，`success` |
| Artifact name | `mydca-android-v0.21.0-c7167576ac8ccb6c5b51d6baabc7b74cf1b48469` |
| Artifact ID/link | [11527146228](https://github.com/TimeLordTTY/MyDCA-Board/actions/runs/37724100264/artifacts/11527146228) |
| 可用性 | `expired=false`；下载成功；expires `2026-11-07T03:45:34Z`，以 GitHub 实时状态为准 |
| Artifact archive | API `size_in_bytes=31810648`；digest `sha256:c327103c1b7972b22afcb75f143cad8d5ca688a03917bb68d3a8145794275b81`（归档摘要，不能当 APK 哈希） |
| APK filename/size | `MyDCA-Board-v0.21.0-c7167576.apk` / 56,132,510 bytes |
| APK SHA-256（下载字节实核） | `c6e9f41f90dc74f9bf9979439004087031ac79f96b6f116b75d77d522b1399d6` |

## 校验过程与复核命令

通过已授权 GitHub CLI 查询 run、jobs 与 artifacts，下载现有成功 run 的唯一制品至忽略目录 `android-app/build/ci-evidence/37724100264`，不提交 APK 或任何凭据。

```powershell
gh api repos/TimeLordTTY/MyDCA-Board/actions/runs/37724100264
gh api repos/TimeLordTTY/MyDCA-Board/actions/runs/37724100264/jobs
gh api repos/TimeLordTTY/MyDCA-Board/actions/runs/37724100264/artifacts
gh run view 37724100264 --repo TimeLordTTY/MyDCA-Board --log
gh run download 37724100264 --repo TimeLordTTY/MyDCA-Board --name mydca-android-v0.21.0-c7167576ac8ccb6c5b51d6baabc7b74cf1b48469 --dir android-app/build/ci-evidence/37724100264
Get-Content android-app/build/ci-evidence/37724100264/SHA256SUMS.txt
Get-Content android-app/build/ci-evidence/37724100264/build-metadata.txt
Get-FileHash android-app/build/ci-evidence/37724100264/MyDCA-Board-v0.21.0-c7167576.apk -Algorithm SHA256
```

下载的 `SHA256SUMS.txt` 记录相同文件名与上述 APK SHA-256；PowerShell 对实际 APK 字节计算的摘要大小写归一化后完全一致。`build-metadata.txt` 实际内容为 repository `TimeLordTTY/MyDCA-Board`、branch `v2`、commit `c7167576ac8ccb6c5b51d6baabc7b74cf1b48469`、base_url `https://www.timelordtty.cn/`、variant `debug`。仅读取元数据，未访问该服务。

Jobs API 显示 Build debug APK、Prepare artifact metadata、Upload APK artifact 全部 success；实际 job 日志包含 `./gradlew --no-daemon testDebugUnitTest assembleDebug lintDebug`，三个 task 以及 `BUILD SUCCESSFUL in 1m 31s`。这与该提交 workflow 的构建命令一致。

## 五层验收状态

| 层级 | 状态与证据边界 |
| --- | --- |
| 源码本地回归通过 | 沿用 source commit 中 [v0.21 发布硬化报告](mydca_v021_release_hardening_20261008.md#实际验证)：Android 三项命令通过，46 类/299 项、失败/错误/跳过 0；lint 0 errors/2 既有 warnings。本任务未改工程代码，该证据仍适用；不是本任务重新执行 Android 测试的声明。 |
| CI 执行成功 | 上述 run 与 job 的真实 success，实际日志三项 Gradle task 与构建成功已核验。 |
| artifact 可获取 | API 可查询且未过期，本任务实际下载成功；后续下载需仓库权限且受保留期限制。 |
| APK 哈希实核 | 实际下载 APK SHA-256 与同制品 CI 生成的 SHA256SUMS.txt 一致，APK 内部版本也已实核。 |
| 真机安装验收 | **待主人后续验收**；未安装真机，未验证设备登录、断网恢复、TalkBack 或真实环境业务可用性。 |

本任务文档变更验证命令：`cmd /c powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0`（后端 package 与全部 Web 构建；禁止自动修复扩大范围）及 `git diff --check`。最终退出码与结果随本任务本地提交交付。历史发布报告及 CURRENT_DEVELOPMENT_STATE 中“无 CI 证据”描述保留为当时事实，本凭证记录本次实际核验结果。

未连接任何数据库、未运行 migration、未修改财务记录、未调用交易/券商接口、未部署、未外部分发。独立企业微信通知由 AiCore/Hermes 交付链路负责；本凭证不充当通知收据。
