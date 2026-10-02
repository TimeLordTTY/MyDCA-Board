# v0.17 只读风险观察后端

本模块仅供观察，不构成交易建议。没有交易动作字段或订单、账本、结算写入依赖。

## API 与权限

`/api/v2/risk-watch-rules`：POST 创建、GET 分页；`/{id}` GET 详情、PATCH 完整配置替换；`/{id}/evaluate` POST 显式评估；`/{id}/snapshots` GET 历史。分页默认20，最大50。

全部规则及快照通过当前登录 owner_user_id AND owner_family_id（NULL 安全比较）隔离。scope 为 PERSONAL 或 FAMILY；FAMILY 每次操作均校验当前家庭管理员，财务读路径仅传该 family。不能指定其他 owner/family。规则修改后旧快照保持原样。

配置例：
```json
{"scope":"PERSONAL","type":"CONCENTRATION","productId":1,"threshold":0.3,"severity":"WARNING","muted":false}
```

支持 RETURN、DRAWDOWN、ALLOCATION_DEVIATION、STALE、CONCENTRATION、NOTE。severity 仅 INFO/WARNING/CRITICAL。NOTE 支持中文备注；muted 只控制提醒展示，不改变命中证据。

## 计算口径

- 所有比例使用小数，0.1 为10%；边界包含等号。
- RETURN 为该标的全部账户聚合 `(市值-成本)/成本`，需成本大于0，direction ABOVE/BELOW 分别大于等于/小于等于。
- DRAWDOWN 为现有20日指标回撤绝对值，行情与指标日期须有效且不超过3天。
- CONCENTRATION 为标的聚合市值 / 雷达总资产。
- ALLOCATION_DEVIATION 为 `abs(类别市值/总资产-target)`；assetType 沿用 HoldingInfo.assetType，CASH 为雷达现金余额。总资产包含现金及持仓，不扣负债。无需自动调仓。
- STALE 为行情和估值日期中较旧者的自然日年龄，threshold 为0至3650整数天；缺日期或未来日期 UNKNOWN。
- 无法可靠估值、总资产不完整或为非正数、两次读取的持仓总值不一致时保留 UNKNOWN/null，不以0替代。无该标的持仓时收益和集中度 UNKNOWN；完整组合中不存在某类别为0占比。

快照记录规则ID、源雷达日期、SHA-256 输入hash、status、matched、中文原因、severity、observedValue、threshold、createdAt 和免责声明。hash 包含规则配置、当前owner/family、雷达日期/资产/行情日期、排序后持仓及回撤指标；同一输入复用快照。跨自然日重新评估；静默及非 NOTE 备注不改变源 fingerprint，NOTE 备注属于评估输入。并发重复仅捕获主键冲突并按当前作用域重查；其他存储故障不伪装成功。历史GET不重新评估或更新快照。

## 部署与验证

`backend/migrations/20261002_risk_watch.sql` 仅含两个观察元数据表，人工审核及部署；无自动迁移注册。本任务未连接数据库，未部署脚本，未修改真实财务记录。未部署时API按现有数据库异常处理失败。

测试使用mock Mapper及服务，不启动数据库；覆盖owner/family隔离、家庭管理员、UNKNOWN、阈值等号、收益/回撤、集中度/偏离、静默、幂等及并发冲突和无财务写入。验收还包括全后端单测、post-task compile hook及git diff --check。真实环境schema与客户端体验仍需部署方人工验收。
