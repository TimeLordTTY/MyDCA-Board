# PC 首页资产目标进度速览交付记录

- 任务：`task-mydca-pc-dashboard-goal-progress-glance-20261010`，owner-approved，仅开发测试与本地提交。
- Dashboard 仅新增组件导入及预算卡片后的位置；原有 KPI、结算、预算、建议逻辑不变。
- 首次进入不请求；默认 PERSONAL/CNY，用户手动加载或重试。作用域及六种币种分别过滤；家庭访问仍依赖后端当前身份权限。入口仅手动读取与目标中心导航。

## 数据与读取边界

复用既有 `goalApi.list`、`goalApi.progress` GET，不改 shared API。不轮询、不持久化、不合计、不换汇，不产生订单、交易、转账、预留或财务写操作。当前后端非人民币进度可能 UNKNOWN，不能从目标配置推断资产可用。

列表最多5页，每页20项；满第五页、重复/缺失ID、非法配置或目标日期均拒绝展示，提示进入目标中心。只有短页确认列表完整后才筛选 ACTIVE、作用域和币种；按真实目标日期稳定升序排序，同日保留列表次序，只展示最近3项。进度请求最多3个且并发最多3；错配 goalId、非法质量或原因契约拒绝整个结果。

只有 OK、有限数值完成率（转换百分比也有限）、boolean 完成状态且与比率达标判断一致才展示明确完成率/达标状态。0是已知0，null/undefined/NaN/Infinity不是0；125%等真实比率保留文字，仅视觉进度条夹到0至100%。PARTIAL仅显示接口明确提供的 knownValue，UNKNOWN不展示余额或比率。任意服务端原因不直接输出，采用既有公开原因白名单与脱敏占位，不暴露任意错误消息或身份信息。

日期严格校验1000至9999年的 YYYY-MM-DD 及真实日历日。统计日期缺失/非法显示未知，绝不补设备日期。倒计时仅在接口 daysRemaining/overdue 与目标日期、统计日期一致时显示，并标注相对数据日期；不宣称实时快照。基础契约不足的 OK 降级展示未知完成率。

页面私有 generation、token/身份引用检查及同步深度 watch 保护用户/家庭、币种、作用域、路由变化；卸载、停用及30秒超时立即清空并使旧请求失效。API没有 AbortSignal，已经发送的网络请求不能物理取消；失效后不追加分页/进度请求，晚到结果不能覆盖新身份或新读取。失败清空旧快照，401/403显示权限提示，500/offline显示中文网络提示，可手动重试。

## 本地验证与 Delivery Manifest 证据

- 基线PC Node全量：110/110通过（依赖安装并构建shared后复核原有测试集）。
- 最终PC Node全量：122/122通过，新增12项模型及Vue setup/模板编译测试，覆盖空列表、ACTIVE/日期/作用域/币种、分页边界及跨页重复、错配响应、数据质量、零/超100%、非法日期、权限/网络失败、手动刷新、超时、乱序、身份/家庭切换、路由及卸载/停用、只读入口和导航。
- 专项Vue/TypeScript：`node web/node_modules/vue-tsc/bin/vue-tsc.js --noEmit -p web/pc-app/tests/tsconfig.goalProgressGlance.json`，退出0。
- 全量PC vue-tsc：任务前基线99条历史诊断，最终99条；规范化工作目录前缀与行号后逐条比较，新增0条。**全量type-check没有通过**，未扩大范围修旧错误。
- `npm --prefix web run build:shared`通过；`powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0`通过，包含shared、PC及H5构建与backend跳过测试打包。成功仅stdout/log；hook未修改。
- `git diff --check`通过。
- 验证日志保留在忽略目录/文件：`web/pc-app/tests/.goal-*.log`、`.codex-hooks/logs/latest-build.log`，不提交生成物或秘密。
- 未连接生产/任何数据库，未修改财务记录、后端、shared源码、SQL或部署配置；不push、不部署、不创建后继业务task/Goal。
- 本地证据供AiCore Git→Codex链生成正式Delivery Manifest。当前会话没有AiCore/Hermes交付工具，独立企业微信verified receipt为 **NOT_VERIFIED**，不声称已通知或验收。

## 待实际网页登录验证

人工登录确认真实个人/家庭权限、人民币及其他币种UNKNOWN、统计日期来源、键盘焦点/屏幕阅读器播报、窄窗口与深浅主题、真实离线和超时重试；确认首页初开没有新增goal请求，所有请求仅GET，目标中心跳转正确。测试采用脱敏mock，未读取真实资产快照。
