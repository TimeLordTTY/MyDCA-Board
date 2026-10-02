# v0.19 长期目标 API

基础路径 `/api/v2/goals`，沿用既有认证。

| 方法 | 路径 | 行为 |
| --- | --- | --- |
| POST | / | 创建目标元数据 |
| GET | / | 分页列表，page 默认0，size 默认20、最大50 |
| GET | /{id} | 目标详情 |
| PATCH | /{id} | 完整替换配置，含暂停/归档状态 |
| GET | /{id}/progress | 只读即时进度，不写历史 |

配置示例：
```json
{"name":"长期储蓄","targetValue":1000000,"targetDate":"2030-12-31","currency":"CNY","scope":"PERSONAL","measure":"TOTAL_ASSETS","state":"ACTIVE","note":"用户目标"}
```

名称1至200字符，目标值为正数（最多18位整数、2位小数），目标日期1000至9999年，允许历史日期用于编辑逾期目标。币种为ISO币种，备注最多2000字符。状态 ACTIVE/PAUSED/ARCHIVED 可通过 PATCH 切换；这些状态只管理目标，不触发财务行为，均允许查看进度。

scope 为 PERSONAL/FAMILY；FAMILY 要求当前家庭管理员，所有元数据查询/更新同时绑定当前 owner user 和 current family，不跨 owner 共享。measure 为 CASH（现金）、POSITION_VALUE（持仓市值）、TOTAL_ASSETS（现金+持仓，不扣负债），沿用雷达的叶账户、关联份额与行情新鲜度口径。

现有雷达汇总仅支持CNY，其他币种保留元数据但进度 UNKNOWN，不进行汇率换算。完整选中金额返回 OK/currentValue/knownValue/completionRate/completed；completionRate 为比例小数，保留12位，可超过1，completed 以未舍入金额比较（包含相等边界）。总资产仅一个组成金额已知时 PARTIAL，仅返回 knownValue，currentValue/完成率/是否完成为 null；均未知、作用域不匹配或总额矛盾时 UNKNOWN，不能把未知当0。独立现金/持仓范围无需另一个范围完整。

asOfDate 为雷达日期；daysRemaining 是目标日期减统计日期，可为负；目标当日不过期，次日起 overdue=true。暂停/归档仍展示当前资产统计，不代表冻结当时金额或自动完成目标。

`migrations/20261002_goal_tracking.sql` 只新增目标元数据表，仅提交未部署；不注册自动迁移。`sql/initsql/goal_tracking.sql` 提供相同结构，部署方按环境选择其一，不重复执行。mock 单元测试不连接数据库。进度使用只读事务，只有创建/编辑写目标表，不修改账户、持仓、账本、订单、结算或真实财务记录。
