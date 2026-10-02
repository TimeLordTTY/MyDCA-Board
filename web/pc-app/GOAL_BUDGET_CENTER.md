# v0.19 PC 目标与预算中心

入口：主导航「目标与预算」，路由 `/goal-budget-center`。

- 目标：分页、创建、编辑、暂停/恢复、二次确认归档、详情与只读完成率。
- 预算：分页、创建、编辑、月份/币种/作用域、收入/固定支出/弹性支出/预留项、分类绑定、计划与实际、剩余和超支。
- UNKNOWN/PARTIAL 保留未知金额；已知部分单独展示，不补零、不推断完成或未超支。预留项仅展示计划；计划结余扣除预留，实际结余遵循后端收入减支出口径。
- FAMILY 由后端校验管理员权限；401/403 提示权限/登录问题，共享客户端沿用登录重定向。
- 加载期间阻止重复操作；失败保留编辑表单，读取失败清除旧证据，支持重试。归档目标保留只读详情。
- 仅写目标/预算元数据；进度和对比仅 GET。本页不展示顶部记账、余额重算或行情采集按钮，无资金操作入口。
- 目标进度目前仅支持 CNY；其他币种由后端明确返回未知，不自动换汇。

验证（仓库根目录）：

```powershell
npm --prefix web run build:shared
npm --prefix web/pc-app run build
web/node_modules/.bin/vue-tsc.cmd --noEmit -p web/pc-app/tests/tsconfig.goalBudget.json
node --test web/pc-app/tests/*.test.mjs
powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0
git diff --check
```

请在 shared 构建结束后串行运行测试：tsc 临时生成的未打包 dist 不能直接用于 Node ESM 测试。
全量 `pc-app type-check` 有既有页面错误；本任务使用独立严格配置验证新增页面和模型。
测试使用模拟 API，未连接数据库、未修改财务记录；真实登录、目标环境 migration 与浏览器人工体验仍由部署方验收。
