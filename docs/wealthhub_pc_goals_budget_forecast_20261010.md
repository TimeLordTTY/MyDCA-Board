# 财富中枢 PC 目标、预算与规划情景：独立重建

任务：task-aiw-r2-15-goals-budgets-forecast-20261010；日期：2026-10-12。

本提交从正式 R2-14 基线 a9fdb126a2379e9215cf4f9606cbae69894d068b 创建全新隔离树。
原候选 357b92b1d4a048b895082a622d8e39900af3ccb4 只提供经逐文件审查的代码内容；原失败运行、旧CLI回执、旧本地验收声明均不作为新交付证据。详细授权/来源映射及新鲜验证在 AiCore reports/work-rescue/20261012-r215-independent-rebuild.md 与独立run记录。

## 业务范围

- GoalBudgetCenter：年轻版排版、分栏规划卡、计划/实际/剩余预算说明、目标日期、服务器统计日与预算客户端读取时间。OK真实零、PARTIAL、UNKNOWN独立，不合并预算或跨币种金额，不把超额进度视为可提现。
- 六个月复盘：仅统一 scoped 样式，保留原只读扫描、独立预算、scope/币种、取消、CSV及分页契约。
- 情景工作台：原只读 forecast/compare、后台零收益基线与用户 annualRate 数学情景分开展示；比例范围 -1至1，最多8位小数。共享结余一次、同scope同币种2至8目标、缺失输入保持未知。前端不计算或写实际资产。
- 身份/异步：身份和请求代数防止旧列表、分页、测算迟到；身份/生命周期切换清输入和结果；初始用户hydration重新读取；单/多选重新挂载输入。
- 元数据：既有创建/编辑/下月预算复制、目标暂停/恢复/归档保持原API及人工触发，不增加财务操作。

## 新验收链

本提交只保存业务内容和可重复fixture，不自称已验收。控制器在源码提交之后全新执行：独立基线 npm ci ignore-scripts/shared build/完整类型、全部PC Node、全部18局部tsconfig、完整TS诊断多重集合（允许基线历史错误，新增须0）、原完整hook MaxFixRounds0及Playwright CLI+Edge。
浏览器仅使用全新独立会话及本地静态preview，全部业务API由mock拦截；未声明API/方法失败。验证1440/1100/760/390、深色、导航、真实零/未知、复制取消、单/多目标假设与争用、无JS异常。原始脚本回显须与本提交字节匹配，显式PASS与隔离边界须同时成立。

正式Delivery只由原AiCore Runtime.integrate/Finalizer生成，本地delivery目录的JSON仅为来源描述。fixture不证明真实角色/服务器财务数学端到端；生产数据库、真实财务写入/登录凭据、交易、部署均禁止。完整hook使用mvn -DskipTests package，后端单元/数据库集成未运行。完整PC类型历史错误不宣称通过。
