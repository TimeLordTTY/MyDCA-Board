# v0.20 多目标共享现金流情景

`POST /api/v2/goals/scenarios/compare`，只读事务，无状态，无 SQL/migration 或业务写入。仅接受两个用户输入情景，每个选择相同的 2—8 个不重复、owner 可见目标。所有目标必须具有相同作用域及币种；不合并 PERSONAL/FAMILY 资金池。授权复用既有目标及预算服务。

请求示例（cashflow 契约见 GOAL_CASHFLOW_FORECAST_API.md）：

```json
{"scenarios":[
  {"name":"方案A","cashflow":{"startMonth":"2026-11","endMonth":"2026-11","mode":"PLANNED","months":[{"month":"2026-11","budgetId":"budget-id","cashflowCovered":true}],"monthlyExtraSavings":0,"annualRate":0},"allocations":[{"goalId":"goal-a","monthlyAmount":200},{"goalId":"goal-b","monthlyAmount":null}]},
  {"name":"方案B","cashflow":{"startMonth":"2026-11","endMonth":"2026-11","mode":"PLANNED","months":[{"month":"2026-11","budgetId":"budget-id","cashflowCovered":true}],"monthlyExtraSavings":0,"annualRate":0.03},"allocations":[{"goalId":"goal-a","monthlyAmount":100},{"goalId":"goal-b","monthlyAmount":100}]}
]}
```

额度显式输入非负金额，最多18位整数/2位小数。省略/null 标 UNSPECIFIED，绝不分配剩余资金；本接口不提供自动权重分配。现金流范围、收益、覆盖声明等校验沿用单目标引擎。

每月 `sharedUpperBound` 是同一预算池（扣支出及预留后）的上限，额外储蓄只在池中计入一次。`specifiedTotal` 是全部已填写额度之和；`overLimit` 为 null 表示上限未知。总额度超限时所有目标该月累计进度 UNKNOWN，不输出把同一结余重复使用后的达成预测。指定额度只是投入上限；实际模拟投入仍受目标剩余缺口限制，完成后剩余额度不自动转给其他目标。UNSPECIFIED 情况只校验已填部分，不代表整体方案可行。

各目标返回 `targetDate`、原始实际进度、假设投入、累计进度、缺口、达成月及 `deadlineStatus`；AFTER_TARGET_MONTH 表示假设达成月晚于目标月。按月模拟不能确认具体日是否按时。当前资产口径可能在目标之间重叠，不得将目标起始资产相加；共享守恒约束只应用于新增投入。

两个情景复用请求内缓存的授权目标/进度/预算/实际读数，不重复获取同一来源。计算结果相同输入及证据下确定，读取时间自然不同。返回 `readStartedAt/readCompletedAt/source`，目标统计日 `asOfDate`、预算 ID、实际 comparison、假设及 UNKNOWN/PARTIAL 原因均绑定结果。跨来源快照非原子实时读取，不将读取时间当行情新鲜度保证。收益缺省仅生成零收益基线。

`changes` 按字段列出额度、起止月、收益、模式、逐月预算/覆盖声明及额外储蓄变化。不会选择最优方案、保存计划、执行交易、入账、占用或转移资金。

验证：mock 服务单元测试，不连接数据库；`mvn -f backend/pom.xml clean test`、编译 hook（BackendOnly 避免超出 backend 授权安装前端依赖）、`git diff --check`。
