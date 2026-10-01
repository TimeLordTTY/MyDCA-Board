# v0.16 研究方案到受控回测闭环

- 新增 `POST /api/v2/research-plans/{id}/runs`，请求为 `{"dataset":"nav.csv"}`。
- 当前 owner/user + family 作用域读取方案；DRAFT/ACTIVE 可运行，ARCHIVED 拒绝。策略/version 与 paramsDraft 取自保存的方案，不接受调用者提供代码、策略或执行器。
- 复用 BacktestLabService 的现有白名单、参数边界、数据目录校验和 Python 离线引擎。通过校验后补齐引擎默认参数并统一数值类型；整数参数 1.0 规范为整数 1。
- 每次请求创建独立 historyRunId；缓存命中也新增历史，不覆盖旧记录。成功与执行/校验失败记录保存 researchPlanId；成功结果附 research_plan_id。
- 历史记录保留策略/version、canonicalParams/paramsHash、实际数据 SHA-256、时间、状态和失败原因码。失败码为 VALIDATION_FAILED、DATASET_READ_FAILED、ENGINE_FAILED 或 ENGINE_TIMEOUT；无可信引擎结果时 engineVersion 为 null，不伪造版本。非法参数不保存任意输入文本。
- 现有 history/detail 接口读取关联；compare 的安全结果与 evidence ZIP 的 manifest/compare.json 保留 research_plan_id。旧历史文件缺少关联时按 null 读取，不需要新增 SQL migration。
- 数据发生变化时使用当前文件 hash 并产生新证据；原方案证据快照保持不变，既有只读警告继续提示差异。新运行不自动改写方案或切换状态。
- 提示：**回测表现不代表未来**。无订单、结算、账本或真实交易渠道依赖。

## 验证

运行 `mvn -f backend/pom.xml test`、`python -m unittest core.backtest.test_engine`、`powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1` 和 `git diff --check`。
新增临时目录/模拟方案测试覆盖多次运行、重启持久化、owner/family 隔离、数据 hash、非法版本/代码参数、参数边界、缺失数据、失败 run、compare/evidence 关联。测试未连接生产数据库，未执行迁移或修改财务记录。
