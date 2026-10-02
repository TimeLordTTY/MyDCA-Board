# 财富中枢文档索引与事实源优先级

- **作者**：ChatGPT
- **更新时间**：2026-10-02 +08:00

## 读取顺序

1. `AGENTS.md`、`AGENT.MD`：仓库执行规则与验证要求。
2. `docs/CURRENT_DEVELOPMENT_STATE.md`：当前版本、已完成能力、安全边界、真实待办。
3. `docs/Phase3-开发进度总结.md`：Phase3 时间线与能力演进。
4. `docs/Phase3-移动端原生App与自动记账.md`：移动端/草稿闭环架构。
5. 当前任务对应专项文档。
6. `docs/财富中枢系统完整设计方案.md`、`docs/wealth_hub_design_full_v1.2.md`、`docs/开发实施指南.md`：目标架构与长期计划。

## 冲突处理

**代码/测试/迁移脚本 > CURRENT_DEVELOPMENT_STATE > Phase3 当前总结 > 专项文档 > 长期设计文档 > 历史 Phase1/Phase2 记录**。

版本专项文档记录当时任务事实；其历史“下一步/未完成”若已被后续任务完成，不得覆盖当前状态。

任何“当前处于初始化阶段”“Android 未接真实登录/OCR/通知监听”“draft_ledger_entry 尚未落地”等旧表述都只能按历史上下文理解。

## 当前关键文档

- v0.17 发布硬化与回归：`docs/mydca_v017_release_hardening_20261002.md`

- v0.16 发布硬化与回归：`docs/mydca_v016_release_hardening_20261001.md`
- 当前状态：`docs/CURRENT_DEVELOPMENT_STATE.md`
- v0.16 PC 研究方案工作台：`docs/mydca_v016_research_workbench_pc_20261001.md`
- v0.16 研究方案回测闭环：`docs/mydca_v016_research_backtest_loop_20261001.md`
- v0.16 研究方案后端：`docs/mydca_v016_research_plan_backend_20261001.md`
- Android：`android-app/README.md`
- v0.16 Android 研究方案只读查看：`docs/mydca_v016_research_view_android_20261001.md`
- 后端：`backend/README.md`
- 数据库增量：`sql/updatesql/README.md`
- v0.7：`docs/mydca_android_v07_quick_capture_hub_20260927.md`
- Android v0.8.0：`docs/mydca_android_v080_external_share_capture_20260927.md`
- Android v0.9.0：`docs/mydca_android_v090_home_widget_quick_capture_20260928.md`
- v0.10.0 转账草稿闭环：`docs/mydca_v010_transfer_draft_loop_20260928.md`
- v0.11.0 投资买入 / 申购草稿闭环：`docs/mydca_v011_invest_buy_draft_loop_20260928.md`
- v0.12.0 投资卖出 / 赎回草稿闭环：`docs/mydca_v012_invest_sell_redeem_draft_loop_20260928.md`
- v0.13.0 人工结算预览与二次确认闭环：`docs/mydca_v013_manual_settlement_preview_confirm_20260928.md`
- v0.8 后端强幂等：`docs/mydca_v08_draft_strong_idempotency_20260927.md`
- v0.14 草稿生命周期审计与安全恢复：`docs/mydca_v014_draft_lifecycle_audit_20260928.md`
- v0.14 人工结算审计与只读对账：`docs/mydca_v014_settlement_audit_reconciliation_20260929.md`
- v0.14 发布硬化与验证：`docs/mydca_v014_release_hardening_20260929.md`
- v0.15 发布硬化与回归：`docs/mydca_v015_release_hardening_20260930.md`
- v0.15 回测运行历史：`docs/mydca_v015_backtest_run_history_20260929.md`
- v0.15 回测对比报告：`docs/mydca_v015_backtest_compare_report_20260929.md`
- v0.15 每日财富雷达后端：`docs/mydca_v015_finance_radar_backend_20260929.md`
- v0.15 只读策略研究候选：`docs/mydca_v015_strategy_research_candidates_20260929.md`
- v0.15 研究证据导出：`docs/mydca_v015_research_evidence_export_20260929.md`
- 自动执行/交付：以 `TimeLordTTY/ai-core` 的 `CURRENT_STATE.md` 与 Delivery Manifest 为准。
- v0.17 只读风险观察后端：`docs/mydca_v017_risk_watch_backend_20261002.md`

- v0.17 风险提醒历史：`docs/mydca_v017_risk_alert_history_20261002.md`
- v0.17 PC 风险观察中心：`docs/mydca_v017_risk_center_pc_20261002.md`
- v0.17 Android 风险观察中心：`docs/mydca_v017_risk_center_android_20261002.md`
