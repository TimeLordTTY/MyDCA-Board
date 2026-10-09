# PC 待结算清单可信状态交付（2026-10-09）

## 实现与边界

独立清单在原有 loadData 触发点启动既有只读 GET，不再依赖账户、资产、今日待办或建议读取成功。idle/loading/error 都明确未知；仅通过结构验证的数组可进入 success，只有成功空数组显示“暂无待结算订单”。刷新立即清空旧行，失败可人工重试，没有轮询。

整个响应拒绝非数组、缺失/异常/重复 orderId、未知订单类型及非 PENDING 状态，不过滤为假空。金额缺失/null/NaN/Infinity 显示未知，真实数值 0 保留；名称、合法 ISO 日期、币种缺失显示未知并标记数据未完整。金额不默认人民币，接口当前记录数不冒充全量或今日待办统计。当前 Order 契约未提供币种/名称时会正常显示未知。

组件局部 generation 与 token/user（包括家庭、角色）/路由身份快照阻止晚到响应；同步 watcher 清空旧数据，卸载及 deactivation 禁用加载，activation 本身不新增 GET。身份变化后可手动重新读取。没有新增写 API、财务计算、结算自动化或日志；确认结算按钮仍仅传 orderId，原详情读取、preview、人工二次确认、freshPreviewToken 调用链逐字保持。

## 验证证据

- 锁文件依赖安装后构建 shared，未修改依赖清单或锁文件。
- 改动前 PC Node suite：80/80；改动后完整 suite：87/87。
- 新增测试覆盖初始/加载/成功空/N、500/401/403/offline、重试、旧行清空、乱序成功/失败、token/家庭/角色/路由变化、卸载/激活、非法结构及字段、未知金额与真实0、缺日期/名称/币种、非 PENDING、上游失败独立读取、待办隔离与原人工处理链保持。
- 局部 vue-tsc：`npx vue-tsc --noEmit -p pc-app/tests/tsconfig.pendingSettlementList.json` 通过。
- 全量 vue-tsc **未通过**：基线99条诊断，改后99条；归一化受行号偏移影响的位置后逐条比较一致，没有新增诊断。不扩大范围修复既有错误。
- shared/PC/mobile 构建及 backend package：`powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0` 通过；hook 成功仅 stdout/log，无 Beep/Popup。
- `git diff --check` 通过。

测试为纯模型运行与 Vue 源码连接/处理链静态断言，未宣称真实浏览器端到端验证。真实登录角色切换、网络离线/恢复、键盘/读屏体验及详情/预览操作仍需人工验证。未连接生产数据库、修改真实财务记录、执行交易、部署或推送。

## Delivery manifest（本地工程交付）

- task_id：task-mydca-pc-pending-settlements-honest-state-20261009
- target：TimeLordTTY/MyDCA-Board@v2；在调度器提供的隔离工作树本地提交，不切换/合并/推送分支。
- allowed_paths：web/pc-app/** 与本专项文档；无其他受版本控制文件修改。
- result_commit：本专项文档所在的本地交付提交（以 `git log -1` 的实际 SHA 为准）。
- checks：Node 87/87、局部类型检查 PASS、全量类型检查 BASELINE_FAILURE_UNCHANGED_99、compile hook PASS、diff check PASS。
- delivery_status：LOCAL_COMMIT；AiCore 外部 Delivery Manifest 登记及独立 Hermes 企业微信完成回执尚未执行/验证，本地测试不作为通知送达证据。
- 无后继 task/Goal、部署、迁移、财务写入。
