# v0.14 人工结算审计与只读对账

## 范围

- `GET /api/v2/settlements/history` 返回当前登录用户已完成结算的历史。
- `GET /api/v2/settlements/history/{orderId}` 返回一笔结算的订单、产品、输入、出资行、结算流水、分录和中文对账原因。跨用户订单统一返回 404。
- 对账只执行读取：`OK` 表示可验证的字段一致，`WARNING` 表示历史记录或关联账户缺少不可变事实，`BROKEN` 表示状态、流水、分录或金额/份额矛盾。它不补账、不冲正、不改持仓。
- PC 结算页和 Android 待结算页均有历史入口；草稿的 `confirmOrderId` 可导航到该订单的审计。页面加载只调用 GET，不调用 confirm。

## 最小审计事实与部署

新结算在 `settlement_confirm` 保存 `ledger_txn_id`（精确指向结算流水）和 `preview_digest`（对确认令牌加域前缀再 SHA-256 的展示摘要）。摘要不能作为 `freshPreviewToken` 使用。结算和链接在同一事务中写入。历史记录缺少这些字段时，流水只能按现有“订单结算:”备注识别，结果标为 `WARNING`。

需由部署人员审查并手动执行 `sql/updatesql/20260929/01_settlement_audit_link.sql` 后再部署后端；本任务未连接数据库，也未执行 migration。既有历史不能补出原始 preview 指纹或结算前账户份额。关联账户的 `initial_shares` 不是不可变历史，相关结算标为 `WARNING`，需人工核对。

## 验证

后端 Mockito 测试覆盖 BUY、SUBSCRIPTION、SELL、REDEMPTION 的正常对账、分录缺失、错份额、越权读取与只读边界。执行完整后端测试、Android 单元测试/组装/lint、PC/shared 构建、`git diff --check` 和仓库 post-task hook。
