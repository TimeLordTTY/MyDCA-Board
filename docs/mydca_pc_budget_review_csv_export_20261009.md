# PC 六个月预算回顾：手动 CSV 导出 · 2026-10-09

任务：`task-mydca-pc-budget-review-csv-export-20261009`。基线为 `1d4b28f81f9f23036396d2bd66c62825f6eeaadd`。

## 实现与边界

- 仅回顾展开、完整加载成功、没有 loading/error 且父页面未禁用时显示“导出 CSV”。点击才在浏览器生成本地副本，不重新 GET、不调用写接口、不上传、不持久化。
- 关闭、取消、卸载、作用域/币种、用户/token、父页面 revision/disabled 变化同步清除缓存并使导出失效。原分页扫描限制与失败清除机制保留，不导出部分扫描或过期响应。
- `budgetReviewCsv.ts` 提供纯序列化、固定安全文件名及独立下载函数。月份升序、预算 ID 稳定排序，每个独立预算一行，空月“未创建预算”、金额未知；只取当前筛选范围，不合并预算或重算家庭总额。
- 18 列包含计划、实际、质量、超支、未匹配数及读取时间。只有 `completeActual` 成立才导出实际金额与超支判断；有限 number 保留合法零，缺值/非有限数/后端数字字符串为未知。计划预留不代表实际转账或支出，读取时间不代表账务快照。
- UTF-8 BOM、全单元格双引号、双引号转义、CRLF 行分隔。文本公式前缀及空白/控制字符前缀用单引号中和。自由文本 warnings 省略，证据使用固定边界说明，不导出凭证、账号身份或原始流水。
- Blob URL 在下载触发后延迟 1 秒 revoke，临时链接在成功/异常后移除。可检测的浏览器异常显示中文错误；浏览器静默拒绝或下载后的文件状态无法由页面证明。

## 验证

- `node --test web/pc-app/tests/*.test.mjs`：54/54 通过。新增6项覆盖跨年、同月独立预算、筛选隔离、空月、完整/部分/未知/缺字段、零与非有限数、中文/逗号/引号/CRLF、公式/控制前缀、稳定顺序/列数、缺浏览器环境、成功/失败 URL 清理、手动点击及身份/筛选/取消失效、不增加请求、中文失败提示。
- `node web/node_modules/vue-tsc/bin/vue-tsc.js --noEmit -p web/pc-app/tests/tsconfig.budgetReview.json`：通过，覆盖本组件与导出模型。
- 全量 PC vue-tsc **未通过**：任务前与当前均99条历史诊断，逐行完全相同，无新增。`tests/typecheckBudgetReview.cjs` 通过内存读取替换本组件为任务前提交源码进行基线对照；`BUDGET_REVIEW_BASELINE=1` 为基线，否则当前。未修复无关旧代码。
- `cmd /c powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0`：通过，含后端跳过测试打包与 shared/PC/mobile 构建。首次工作树无前端依赖，hook 安装后成功；shared 生成前的首次针对性测试因缺 dist 失败，构建后完整测试全部通过。
- `git diff --check`：通过。hook 未修改，成功输出仅 stdout/log，符合仓库非侵入规则。

## 交付与人工验收

只修改授权 PC 路径及本说明，提交本地不推送。不修改任务文件、共享状态文档、后端、Android、数据库或预算 CRUD；未连接生产数据库、未改财务记录、未操作资金/交易/部署。

请在授权浏览器人工点击导出，核对六个月与当前表格、同月独立预算、空月/未知/零值；用 Excel 或其他表格软件确认中文、字段与公式防护效果，并验收下载权限拒绝、保存位置及文件实际可用性。自动化夹具不等于真实浏览器下载验收。

本会话不额外发送消息或创建任务；independent 企业微信交付由既有 AiCore/Hermes 链接续，未在本会话验证通知收据。
