# Android 目标进度与本月预算只读查看

总览「查看目标进度与本月预算」进入；返回键或返回按钮退出，底部导航关闭页面。
目标每页 20 条，逐条读取 progress，展示目标日期、完成率、剩余值和数据完整性。
预算每页 20 条，只展示设备当前月份匹配的预算；本页无匹配时明确提示并允许继续分页。
计划收入、支出、储备及项目计划与后端一致；实际、剩余和超支以 comparison 为准。
PARTIAL 的已知部分单独标注，不当最终值；UNKNOWN/null 不显示零或未超支。
金额保留 BigDecimal 精度，完成率按后端小数比例转换百分比。

目标与预算观察使用四个 GET（新增预测接口见下文）：goals、goals/{id}/progress、monthly-budgets、monthly-budgets/{id}/comparison。
无创建/修改或持久化写请求，无新增权限，无本地持久化，无金融副作用。
401 复用会话过期流程，403 明确无权访问；网络/服务失败显示中文重试，刷新清空旧数据。

本地验证：testDebugUnitTest、assembleDebug、lintDebug、post-task compile hook、git diff --check。
测试仅连接本地 MockWebServer；真机、目标环境和部署状态仍需人工验收。

发布收口：Android 0.19.0 / versionCode 20，CI 预期命名同步但尚无真实 CI 制品证据。作用域中文显示，收入不适用超支判断，预留显示仅计划，加载与错误使用 polite liveRegion。详见 `../docs/mydca_v019_release_hardening_20261002.md`。

## v0.20 目标预测

目标卡片「查看目标预测」进入独立只读页，返回恢复目标列表。使用来源统计月之后连续12个月、PLANNED、额外储蓄0，不传 annualRate，只显示后端零收益 baseline。先 GET progress 和分页读取预算（最多100页，超限报错），只关联同作用域同月唯一预算；重复预算不任意选择。cashflowCovered 始终 false，Android 不代替主人确认完整现金流，所以现有预算通常返回 PARTIAL，未选择预算返回 UNKNOWN。逐月原因由后端中文 reason 展示。

唯一新增 POST 是后端明确只读事务的 goals/{id}/forecast，不保存情景、配置或执行资金计划，无其他写请求。显示实际完成率/缺口、逐月计划与假设值、假设达成时间、来源日期；不完整数据不声称达成。来源早于设备日期明确过期；刷新及错误清除旧结果，允许人工重试，401/403 复用既有会话处理。测试仅使用本地 MockWebServer，不连接业务数据库。
