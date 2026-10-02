# v0.19 月度预算 API

基础路径 `/api/v2/monthly-budgets`，使用既有认证。

| 方法 | 路径 | 行为 |
| --- | --- | --- |
| POST | / | 创建预算元数据 |
| GET | / | 分页列表，page 默认0，size 默认20、最大50 |
| GET | /{id} | 预算详情 |
| PATCH | /{id} | 完整替换预算配置 |
| GET | /{id}/comparison | 即时只读计划/实际对比 |

```json
{"name":"十月预算","month":"2026-10","currency":"CNY","scope":"PERSONAL","items":[{"name":"工资","kind":"INCOME","categoryId":1,"planned":10000},{"name":"房租","kind":"FIXED_EXPENSE","categoryId":2,"planned":3000},{"name":"消费","kind":"FLEXIBLE_EXPENSE","categoryId":3,"planned":2000},{"name":"预留","kind":"RESERVE","categoryId":null,"planned":1000}]}
```

月份严格YYYY-MM（1000至9998年）；名称1至200字符，1至100项，计划金额非负、最多18位整数和2位小数；currency 为ISO币种。收入/支出必须显式绑定既有分类ID，不猜测固定/弹性分类，不修改流水分类。同一收入分类只能绑定一次，同一支出分类只能绑定一次（固定与弹性不能重复）。RESERVE 不绑定分类，只表示计划留存，不代表实际支出、账户余额或资金占用。同月允许多个独立计划，比较各自即时读取流水。

元数据查询与更新同时绑定当前 owner 和 current family，家庭变更后旧计划不可见。FAMILY 配置及读取要求当前家庭管理员。财务读取严格同时绑定 ledger_txn.user_id 和 family_id：PERSONAL 只读当前 owner 的 family_id=NULL 流水，FAMILY 只读当前 owner 在当前家庭的流水；不汇总其他成员流水，不使用宽松 OR 条件。

实际口径为已确认、未撤销的 INCOME/EXPENSE/REIMBURSE_IN/REIMBURSE_OUT 交易中 INCOME/EXPENSE 分录净额。收入 CREDIT 为正，DEBIT 为负；支出 DEBIT 为正，CREDIT 为负。只读取收入/支出分录，避免与现金分录重复计数；投资、转账、订单、手续费和税费交易不包含在此生活预算口径内。退款/报销按其已有分类匹配；分类为空则未匹配，不推断原分类。月份采用 trade_date 的月初包含、下月月初排除；无日期的相关流水保留未知证据。

计划结余=收入计划-固定/弹性支出计划-预留。actualSurplus=收入实际-支出实际，remainingBudget=固定/弹性支出计划-支出实际（不包含预留）。金额可以为负，超支比较严格小于0，相等不超支。每项 remaining=planned-actual；收入项表示计划收入缺口，overspent=null。RESERVE 的实际、剩余和超支均为null，quality=UNKNOWN。总体 overspent 表示总支出超计划；单项超支额外输出 warnings，即使总预算还有余额。

成功读取的空流水集合是已知零。缺失金额、非法方向、负分录金额、币种不匹配、未匹配分类、缺失分录或日期不会按零计算，也不做外汇换算。完整统计返回OK；部分已知返回PARTIAL，完全无有效金额证据返回UNKNOWN。任何不完整证据使总体 actualIncome/actualExpenses/actualSurplus/remainingBudget/overspent 为null；各项 knownActual 保留已读金额，actual/remaining/overspent 仅在完整时提供。warnings 提示负计划结余、单项超支及缺失证据，unmatchedPostings 提示未匹配证据行数。缺失数据库表/数据库读取故障按既有后端错误处理，不伪装成空集合。

`migrations/20261002_monthly_budget.sql` 与 `sql/initsql/monthly_budget.sql` 提供相同表结构，部署方择一人工部署；仅提交，未部署，无自动 migration 注册。只有创建/编辑写 monthly_budget 元数据，对比使用只读事务，不写财务表、历史快照或目标表，不创建订单、不结算、不正式入账。单元测试使用 mock Mapper，无数据库连接。

验证：`mvn -f backend/pom.xml test`；`powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -BackendOnly -MaxFixRounds 0`；`git diff --check`。
