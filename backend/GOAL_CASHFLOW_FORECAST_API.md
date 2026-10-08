# v0.20 只读目标现金流预测

`POST /api/v2/goals/{id}/forecast` 携带情景参数，但只读事务、无持久化。
复用 GoalTrackingService.detail/progress 与 MonthlyBudgetService.detail/comparison 的 owner/current-family 隔离及家庭管理员检查，不新增 SQL、迁移或账本聚合算法。

```json
{
  "startMonth": "2026-11",
  "endMonth": "2026-12",
  "mode": "PLANNED",
  "months": [
    {"month":"2026-11","budgetId":"nov-budget-id","cashflowCovered":true},
    {"month":"2026-12","budgetId":"dec-budget-id","cashflowCovered":true}
  ],
  "monthlyExtraSavings": 0,
  "annualRate": 0.03
}
```

月份严格 YYYY-MM，1000—9998 年，范围包含首尾、1—60 月，months 必须按连续月份排列；每月最多选择一个既有预算，避免同月多个计划重叠计数。预算月份/作用域不一致拒绝请求。预算 ID 可为 null，结果 UNKNOWN。cashflowCovered 默认 false，须由用户明确确认该月预算覆盖全部相关现金流（包括既有预算聚合不涵盖的投资/费用/税费等），否则 PARTIAL；这只是固定情景声明，不是系统核实的事实。不可将预留或不可动用资金声明为外部额外储蓄。

模式必须显式选择：PLANNED 从统计月之后开始，使用计划收入/支出/预留；ACTUAL_PLUS_REMAINING 可从统计月开始，复用实际对比，按每个分类“actual + max(planned-actual,0)”计算整月情景收入/支出。实际不完整保留原 comparison 的 UNKNOWN/PARTIAL、knownActual 和 warnings。统计月 contribution 上限另扣 actualSurplus，避免重复计入已经在资产进度中的已发生现金流。过去月份拒绝；统计月与预测范围之间有未覆盖的整月时累计进度 UNKNOWN，不给新达成日期。

每月 surplusUpperBound = max(情景收入 - 情景支出 - 计划预留 + 显式额外储蓄, 0)，统计月按上述规则扣除已发生结余。额外储蓄默认零，表示预算之外主人明确输入的每月外部自由现金；赤字会先抵消此额外现金。contribution 不超过这个已知非负上限，也不超过收益计入后的目标剩余缺口。RESERVE 从可用额度扣除，不读取或释放真实占用资金。当前资产值仍沿用目标口径（可含受限资产），不被当作可投入额度。只模拟新增投入，不模拟为赤字提款；UNREACHABLE_WITHIN_RANGE 表示仅在当前输入与范围内无法达成。

响应 actualProgress/asOfDate 是即时实际读数；fixedInputs 是固定输入；baseline 始终为 0% 情景。每月返回 startingProgress、plannedIncome/Expenses/Reserve、actualReading（仅实际模式）、modeledIncome/Expenses、surplusUpperBound、contribution、mathematicalReturn、cumulativeProgress 和 remainingGap。baseline/mathematicalScenario 各自有 quality、outcome、achievedMonth 和完整 months。字段 null 保持未知；前序未知使后续累计值未知。已在统计日完成的目标可返回统计月，并仍保留后续现金流的不完整状态。

annualRate 可省略，省略不生成附加情景；显式提供时另列 mathematicalScenario，范围 -1 至 1（比例而非百分数），最多8位小数。不替用户选择收益。收益采用 annualRate/12 的月简单计息，再月末投入；当月也按整月处理，是粗粒度数学假设。BigDecimal 金额2位小数向零舍入，上限不会因舍入而增大。计划模式从统计日资产开始，不假设统计月剩余现金流变化。不是市场预测，不写收益或投入到余额，不涉及实际交易或资金调拨。

预算币种不匹配不换汇，UNKNOWN；目标非 CNY、缺失行情或汇率沿用进度质量。POSITION_VALUE 目标未指定投入账户，不假设自动买入，UNKNOWN。暂停/归档目标仍允许只读模拟，无状态或真实财务记录修改。

验证使用 mock 服务/Mapper，不连接数据库：`mvn -f backend/pom.xml test`，post-task compile hook，`git diff --check`。
