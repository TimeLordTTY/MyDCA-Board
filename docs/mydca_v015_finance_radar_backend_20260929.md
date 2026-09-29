# v0.15 每日财富雷达后端

`GET /api/v2/finance-radar?scope=PERSONAL|FAMILY` 返回现有记录的只读聚合，默认 `PERSONAL`。身份取自登录态，不接受调用方提供的 userId 或 familyId。`FAMILY` 仅允许当前家庭管理员，账户、持仓与草稿查询只传家庭 ID；`PERSONAL` 只传当前用户 ID。订单模型只有 `user_id`，所以家庭视图中的订单与对账计数为 `null`，并返回 `FAMILY_ORDER_SCOPE_UNKNOWN`。

响应含 `assets`（现金、投资成本、持仓市值、负债、总资产、净资产和状态）、`counts`（DRAFT、Outbox、PENDING、待结算、对账 WARNING/BROKEN）、各持仓产品的行情与指标日期/状态，以及 `warnings`。未知金额或计数为 `null`，不能显示成零。无账户或持仓时，已知的空集合汇总为零。

持仓估值复用现有持仓服务的 NAV 口径；每个产品分别返回 `priceDate`、`valuationDate`、`indicatorDate`。行情、NAV 或指标缺失为 `UNKNOWN`，日期超过 3 个自然日或晚于今天为 `WARNING`。行情或 NAV 不可靠时，总资产与净资产为 `null`。账户余额缺失、非 CNY 账户/产品、关联产品账户缺有效份额时，资产汇总为 `UNKNOWN`。此阈值是数据质量提醒，不代表交易日历或投资判断。

Android Outbox 是设备本地加密队列，后端无法查询；`counts.outbox` 永远为 `null`，并带 `OUTBOX_UNKNOWN`。该 GET 不调用 parse、draft 创建、preview、confirm、订单创建、结算或账本写入，不生成买卖/调仓建议。

验证：后端单元测试覆盖身份作用域、空数据、待办与 BROKEN 对账、缺失/过旧行情、未知金额和无写副作用；最终运行 `mvn -B test`、仓库 post-task hook 与 `git diff --check`。
