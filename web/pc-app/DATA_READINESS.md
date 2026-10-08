# v0.21 PC 数据就绪检查

入口：PC 导航「数据就绪检查」(`/data-readiness`)。
复用 shared JWT 客户端，只调用 `GET /api/v2/data-readiness?scope=PERSONAL|FAMILY&month=YYYY-MM`。
个人沿用 owner 隔离，家庭由后端验证管理员权限；客户端不传 owner/family ID。

资产与行情、目标与预算、风险与配置观察、研究/回测、规划情景、部署前提分别显示状态、证据来源、来源时间和人工下一步。缺失项保留 UNKNOWN；部分接口失败保留 UNAVAILABLE；月份是统计期间，不冒充更新时间。超过三个日历日的日期来源显示过期提示，最终完整性以各项诊断说明为准。

刷新失败清除旧证据；切换作用域或月份清除当前报告。只允许一个刷新请求在途。401/403 沿用 shared 登录处理，页面提供固定中文错误，不展示原始异常。复制仅使用固定领域/状态文案，不复制服务器原文、金额、标识符、来源地址或调试信息。

本页隐藏全局余额重算、行情采集、记账按钮；没有迁移执行、自动修复或标记健康功能。
数据库迁移始终显示「未核实部署」。离线报告来源为 `docs/mydca_v021_migration_preflight_20261008.md`，只说明静态覆盖，数据库演练未执行，目标环境需人工验收。

本地验证命令（web 目录）：

```text
npm run build:shared
npm run build:pc
node --test pc-app/tests/dataReadiness.test.mjs
```

仓库根目录执行 post-task compile hook 和 `git diff --check`。
页面回归使用模拟 API 与 Vue SFC 编译，覆盖权限、空态、部分证据、过期、失败恢复、重复刷新、剪贴板失败、脱敏与 GET 契约。真实浏览器键盘体验、断网、目标环境部署与真实权限仍需人工验收；本任务不连接数据库或修改财务记录。
