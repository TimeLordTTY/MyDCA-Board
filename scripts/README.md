# Python 脚本说明

本目录保存财富中枢的行情、指标、调度及其他任务型 Python 工具。它们不是 AiCore 自动研发链本身，也不能因为脚本存在就推断 Hermes 已获准调用。

当前工程状态与任务优先级请先读 `../docs/CURRENT_DEVELOPMENT_STATE.md`。

## 主要目录

- `market/`：基金/ETF 行情、净值与历史补数据。
- `indicator/`：MA、MACD、RSI 等指标。
- `scheduler/`：项目内部调度工具。
- 其他目录以仓库实际文件为准。

## 安全规则

- 不把真实数据库密码、Token、Cookie、API key 写入 Git。
- 涉及数据库的脚本只能在任务明确授权并确认目标环境后执行。
- AiCore/Hermes 不得因为这里存在脚本就自动运行生产数据任务。
- 新脚本应有明确参数、非 0 失败退出码、脱敏日志和可复核输出。

## 开发与验证

按具体子目录 README / requirements 安装依赖并执行最小相关测试。任何仓库代码或项目文件修改后，最终仍需运行根目录 `scripts/post-task-compile-hook.ps1`；成功只写 stdout/log，不播放声音、不弹 Windows 通知。

## 历史说明

早期 README 中曾给出固定数据库连接示例与固定调度时间。实际运行配置必须以当前环境配置和对应脚本为准，文档不再把示例连接参数或时间表当生产事实。