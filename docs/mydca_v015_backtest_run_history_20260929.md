# v0.15 回测运行历史

回测实验室每次 `POST /api/v2/backtest-lab/runs` 都写入独立的 `history_run_id`。引擎原有的确定性 `run_id` 保留，用于识别相同数据和策略产生的相同结果。响应新增 `history_run_id`、`cache_hit`；缓存命中仍写一条历史记录，`cache_hit=true`。

历史默认保存在后端工作目录相对路径 `../data/backtest-history`，可由 `backtest.history-root` Java 系统属性指定。每条记录以随机 UUID 命名，写入时先写临时文件再原子移动；应用重启后直接从文件恢复，不依赖数据库迁移。运行目录必须由部署方提供持久化存储并限制操作系统访问权限。仅部署代码不会自动迁移或连接生产数据库。

`GET /api/v2/backtest-lab/runs?page=0&size=20` 返回当前登录用户与当前家庭 ID 都匹配的历史摘要，`size` 最多 50；列表不含结果 payload。`GET /api/v2/backtest-lab/runs/{historyRunId}` 返回同作用域下的详情，不可见的 ID 返回 404。既有 `/recent` 从持久历史提供最近 10 条成功结果，失败请求不会挤掉成功记录，兼容原有客户端。

历史包含数据集文件名与 SHA-256、策略和版本、引擎版本、规范化有效参数与 SHA-256、开始和结束时间、状态、指标及受控大小的结果。`INVALID`、`FAILED`、`TIMEOUT` 仅记录通用失败码，不保存异常原文。记录不保存 CSV、文件绝对路径、Token 或 secret；单条序列化记录上限 128 KB。回测服务不依赖订单、结算或账本服务，也不会创建交易或正式财务记录。
