# v0.18 配置与止盈观察 API

仅供观察，不构成交易建议。所有目标、区间、收益和分段阈值由用户显式提交，后端没有默认阈值、AI 推断或自动交易入口。

基础路径 `/api/v2/allocation-policies`，使用既有认证：

| 方法 | 路径 | 行为 |
| --- | --- | --- |
| POST | 基础路径 | 创建策略元数据 |
| GET | 基础路径 | 分页列表，page 默认 0、size 默认 20，最大 50 |
| GET | /{id} | 详情 |
| PATCH | /{id} | 完整替换配置，包含 enabled 启停和 note 备注 |
| POST | /{id}/evaluate | 只读评估，不保存快照或提醒 |
| GET | /{id}/preview | 主动读取数学情景，不保存调整、不创建订单 |

创建和编辑的配置示例：

```json
{
  "scope": "PERSONAL",
  "productId": 1,
  "assetType": null,
  "target": 0.6,
  "lowerBound": 0.5,
  "upperBound": 0.7,
  "returnThreshold": 0.5,
  "takeProfitThresholds": [0.2, 0.5, 0.8],
  "enabled": true,
  "note": "用户自定义观察"
}
```

scope 为 PERSONAL 或 FAMILY；productId 和 assetType 必须且只能配置一个。
FAMILY 创建、读取、编辑和评估要求当前家庭管理员权限。每条查询和更新同时约束当前 owner user 与当前 family，家庭策略也不跨 owner 分享。

比例使用小数，0.1 表示 10%；下限、目标、上限必填，满足 0 ≤ 下限 ≤ 目标 ≤ 上限 ≤ 1。
收益阈值可省略，非负；分段止盈阈值可省略或为空，最多 20 个，非负且严格递增。
enabled 必填，note 最多 2000 字符。CASH 类别使用现金余额，不支持收益阈值。

评估分母为雷达完整总资产（现金 + 持仓市值，不扣负债）；产品按全部持仓汇总，类别按 assetType 汇总。
上下限均包含边界，比较使用未舍入金额；展示占比和收益率保留 12 位小数。
收益率为（选中市值 − 选中成本）/ 选中成本。任一收益阈值达到（含相等）时优先返回 TAKE_PROFIT_WATCH；结果保留 allocation、returnRate 与 reachedTakeProfitThresholds，后者列出所有已达到的分段阈值，不表示任何卖出动作。

其余状态为 IN_RANGE、BELOW_BAND、ABOVE_BAND、UNKNOWN，均返回中文 reason。
行情 UNKNOWN/STALE、缺失分类、市值、份额、选中持仓成本或汇总不一致返回 UNKNOWN，不以 0 替代。
无选中持仓时完整组合可以确定占比为 0，但配置收益观察阈值后仍为 UNKNOWN，因为无收益成本依据。
停用策略返回 UNKNOWN 和停用原因，不读取财务数据。

评估仅调用只读雷达与持仓查询，不写策略、快照、订单、结算、账本、账户或持仓。
新表脚本为 `migrations/20261002_allocation_policy.sql`，仅提交，未部署，未注册自动 migration runner；部署须另行人工安排。
通用初始化脚本 `sql/initsql/allocation_policy.sql` 与增量脚本提供相同表结构，按环境选择其一，不重复执行。
测试使用 mock 服务和 Mapper，不连接数据库。

## 只读情景与发布校验

GET preview 固定总资产计算到目标点/最近区间边界的选中资产假设金额，其余组合等额反向；舍入至分后取反，合计严格为零，不分配到其他产品，不校验资金可用性。数据不足时金额和权重为 null；税、费用、滑点、交易限制、外汇为 NOT_MODELED，原始价格来源保留 UNKNOWN。

评估和预览均检查快照 scope、现金 + 持仓 = 总资产、唯一有效行情及持仓汇总；缺失/重复行情不会产生正常状态。收益成本未知时评估可以保留已知权重，收益/已达阈值不可解读为零或“未触发”。后端行情新鲜度沿用雷达 3 天窗口；前端日期提示不证明实时行情。发布回归见 `../docs/mydca_v018_release_hardening_20261002.md`。
