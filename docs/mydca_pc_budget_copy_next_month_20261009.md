# PC 月度预算复制为下月计划草稿

- 任务：task-mydca-pc-budget-copy-next-month-20261009；owner-approved，仅本地工程交付。
- 范围：PC 目标与预算中心、计划复制纯函数、PC 回归测试和本文档。

## 实现

预算列表与已授权预算详情提供“复制为下月预算”。仅接受当前页面列表或选中详情的预算对象；复制时重新 GET detail，并核对 ID 与作用域，不从实际对比或跨身份缓存复制。busy 时阻止重复操作、分页和标签切换。身份 token、用户或权限变化清空旧列表、详情和表单；请求代次及身份快照阻止旧异步结果回填，卸载后同样失效。localStorage token 与 store 不一致的变化也在入口和返回结果处检查。

纯函数 copyBudgetNextMonth 先检查运行时数据形状并复用 validateBudget，按整数年/月递增；9998-12 拒绝复制。输出仅 name/month/scope/currency/items，项目仅 name/kind/categoryId/planned，数组与项目独立；不携带 ID、createdAt、实际金额、quality、warnings、流水或确认状态。名称保留原值，允许同月多份独立预算。

新表单 editing 为空，显示计划草稿提示及金额需重新确认的提醒。用户可编辑月份、分类、金额、币种和作用域。打开仅 GET、取消和修改无服务器写入；点击保存并通过已有校验才走已有 budgetApi.create，busy 防止双击重复提交，不调用 edit 修改来源。保存失败保留表单并显示中文权限/失败提示；成功刷新列表、清除详情及六个月回顾。此处“草稿”指尚未保存的本地计划表单，并未引入后端 DRAFT 状态。

## 验证与环境

工作树未安装依赖。为保持所有写入位于 allowed_paths，使用 web/pc-app/.verification 内的完整隔离副本安装依赖和生成 shared、PC、Mobile、backend 构建产物；源文件与测试由当前工作树同步，未修改原 shared/backend/mobile 文件。专项范围外不写入构建产物或依赖文件。

- node --test web/pc-app/tests/*.test.mjs：在隔离副本执行，60/60 通过（原54项 + 新6项）。覆盖月递增、跨年、闰年、上下界、白名单、深拷贝、零值、源不变、畸形配置、超100项、非法分类；取消无写入、失败保留、一次 create 不 edit、合法同月多份、分页与详情来源、非预算/脱离列表/跨作用域拒绝、401/403、busy、token/身份/角色变更和卸载后异步结果丢弃。既有 CRUD、回顾、CSV 回归通过。
- 全量 PC vue-tsc：任务开始 HEAD 与修改后各99条既有错误；忽略因插入代码导致的行列偏移后，文件、错误码和诊断文本完全一致，无新增诊断。不能声明全量类型检查通过。
- 定向 vue-tsc --noEmit -p web/pc-app/tests/tsconfig.goalBudget.json：通过；包含当前页面、计划 model 与被引用组件。shared 构建包含 TypeScript 编译。
- post-task compile hook：通过；backend Maven package、shared TypeScript/Vite、PC Vite 和 Mobile Vite 均成功。对上述隔离副本使用原 scripts/post-task-compile-hook.ps1，显式传入绝对 -RepoRoot 和 -MaxFixRounds 0（防止 hook 自动修复越出授权范围）。最初相对 RepoRoot 导致日志路径解析错误；改为绝对路径后重新验证。
- git diff --check：通过。

## 人工与外部交付边界

真实浏览器键盘/布局、真实角色会话失效跳转、网络断连提示及实际服务端保存后的列表刷新未进行真人或联网验收；测试全部使用模拟 API，不实际写入任何预算或财务记录。未连接生产数据库、调用交易/账本接口、部署或推送。仅本地提交。AiCore Delivery 与 independent 企业微信回执由既有自动链消费 result commit，本执行未发送外部消息，未核验真实回执，不另建任务或 Goal。
