# v0.15 发布硬化与回归

本轮将 Android `versionName=0.15.0`、`versionCode=16` 与 GitHub Actions APK 文件名及 artifact 名统一。任务仅在本地提交，不推送；没有可核实的 v0.15 CI Run、Artifact 或 CI APK SHA-256，故不回填这些值。

## 只读研究与雷达边界

- 回测历史是 owner user/family 作用域的本地文件记录，默认目录 `../data/backtest-history`，需要部署方配置持久化目录及操作系统权限。v0.15 没有新增数据库 migration；目录不可写、历史损坏或请求失败时接口/页面应暴露失败，不把失败伪装为空结果。
- PC 历史对比只读取 2 至 5 条已保存成功 run；研究候选只筛查已保存结果；证据 ZIP 只导出研究字段。三者均不创建订单、结算或交易。研究候选有 `EVIDENCE_INSUFFICIENT` 与 `DOES_NOT_MEET_CRITERIA` 状态，历史表现不代表未来收益。
- PC/Android 雷达只读取服务端事实，资产、计数、行情与指标的未知值保持「未知」；行情过期是数据质量提醒。设备 Outbox 数量仅在 Android 本地可知，PC/后端显示未知。刷新失败时旧快照明确标识为旧结果，并提供重试；401/403 显示登录或权限原因。
- PC 策略实验室区分加载、空历史、失败与权限状态；更改历史选择或阈值会清除旧对比/候选，异步请求返回时不会覆盖更新后的选择。Android 最近回测只读展示，刷新失败保留并标记旧结果，缺失指标显示「未知」。

## 跨模块回归范围

后端全量测试覆盖 DRAFT 创建、preview、confirm 与幂等，TRANSFER、BUY/SUBSCRIPTION、SELL/REDEMPTION、人工 settlement、draft lifecycle、settlement audit，以及回测历史/对比/候选/证据和 Radar；Android 单元测试覆盖 Outbox、share/widget/OCR、草稿与结算等既有流程。PC 构建覆盖共享类型和 Radar/策略实验室页面；Python 测试覆盖回测引擎。自动化验证不代替真实设备操作、真实权限/网络故障体验或目标环境迁移检查。

## 本地验证（2026-09-30）

- `backend`: `mvn -B test`：202 项通过，0 失败、0 错误、0 跳过。
- `web`: `npm ci` 后 `npm run build`：shared、PC 与历史 Mobile H5 均通过；PC 构建有既有大 chunk 提示。
- `android-app`: 设置本机 `ANDROID_HOME` 后执行 `gradlew.bat testDebugUnitTest assembleDebug lintDebug --no-daemon`：40 个测试类、277 项通过；Debug APK 和 lint 通过。`aapt2 dump badging` 实测 APK 为 `versionCode='16' versionName='0.15.0'`。Kotlin 编译有两条既有未使用参数警告。
- `python -m unittest discover -s core/backtest -p test_*.py`：5 项通过。
- UTF-8 严格解码与替换字符检查：10 个变更文件通过。`git diff --check` 通过。
- `scripts/post-task-compile-hook.ps1`：后端 package 与 web 全量 build 通过；成功路径只输出 stdout/log，无 Beep 或 Windows 通知。

本轮没有连接或迁移生产数据库，没有修改真实财务记录，没有交易、broker API 调用或自动订单/结算。v0.8 与 v0.14 已提交 SQL 是否部署需在目标环境另行核实；本轮不声称部署完成。
