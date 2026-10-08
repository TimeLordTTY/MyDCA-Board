# 数据就绪只读诊断

`GET /api/v2/data-readiness?scope=PERSONAL&month=2026-10`，沿用 JWT 登录。
scope 仅 PERSONAL / FAMILY；FAMILY 须当前家庭管理员。不能指定其他 owner 或 family ID。

响应包含 scope、month、checkedAt 和 evidence：area、state、中文 reason、逻辑 source、可观察的 dataTime（未知为 null）、nextStep。
READY 表示该项证据可读，不表示目标达标、预算未超支或可以上线。
PARTIAL 表示部分证据可读但覆盖或时效不完整；UNKNOWN 表示无法确认；UNAVAILABLE 表示授权读取失败。

覆盖目标、预算、风险规则、配置观察、研究、资产、行情、汇率、预测输入和 schema。
元数据最多检查首50项；满页不报全量 READY，首批未命中不证明无数据。
预算只检查请求月份；不自动声明现金流覆盖。预测需人工逐月选择预算并提供覆盖声明。
风险只读规则，不调用会保存快照的 evaluate。研究复用既有证据校验，不发起回测。
行情复用雷达的持仓报价、净值及指标，并保留最早来源日期；3个日历日窗口沿用现有口径，需人工核对交易日。
汇率无可靠读取契约，保留 UNKNOWN。schema 始终 UNKNOWN：业务读取成功和 SQL 文件存在均不证明全部迁移已部署。

管理员与普通 owner 均只得到逻辑来源与固定中文建议，不返回表结构、内部路径、异常原文或数据库账号。
无额外数据库连接、结构探测、外部刷新、金融写入或观察历史写入；所有调用复用既有授权读取。
家庭订单/结算覆盖不完整时资产项保留 PARTIAL。各项失败单独报告 UNAVAILABLE，不把缺失金额转换为零。
