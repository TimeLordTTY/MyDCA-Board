---
task_id: task-implement-hermes-reliable-notification-queue-l3-20260527
title: 实现 Hermes 单微信通道可靠通知队列
 target_agent: codex
task_type: engineering_change
risk_level: medium
auto_level: L3_OWNER_CONFIRMED_FOREGROUND
requires_model: false
requires_confirmation: true
owner_approved: true
approved_by: chatgpt
approval_source: chatgpt
approval_at: 2026-05-27T10:25:00+08:00
approval_expires_at: 2026-05-28T23:59:00+08:00
approval_scope: this_task_only
allowed_execution_mode: foreground_only
execution_profile: safe_hermes_notification_config
profile_action: foreground_engineering_change
created_by: chatgpt-coordinator
created_at: 2026-05-27T10:25:00+08:00
allowed_paths:
  - AGENTS.md
  - CURRENT_STATE.md
  - docs/
  - agent-runtime/hermes/
  - agent-runtime/notifications/
  - agent-runtime/state.json
  - agent-runtime/task_log.md
  - agent-inbox/pending/
  - agent-inbox/done/
  - agent-inbox/failed/
  - agent-outbox/hermes/
  - agent-outbox/codex/
  - reports/codex/
  - reports/hermes/
  - handoff/
  - tests/
forbidden_actions:
  - connect_database
  - run_mydca_board_business_script
  - modify_real_ledger
  - modify_accounts_or_holdings
  - auto_trade
  - commit_sensitive_values
  - configure_nonexistent_alt_channel
---

# 实现 Hermes 单微信通道可靠通知队列

**作者**：ChatGPT  
**创建时间**：2026-05-27 10:25  
**适用范围**：Hermes pending worker、微信/iLink 发送、Codex 到 Hermes 的通知任务。

## 修订记录

| 序号 | 当前作者 | 修改时间 | 修改原因 | 修改内容概要 |
|---|---|---|---|---|
| 1 | ChatGPT | 2026-05-27 10:25 | 上一个可靠通知队列任务被错误标为 L4，Codex 前台处理待办时跳过 | 新建 L3 前台受控任务，要求 Codex 实现单微信通道下的可靠通知队列 |

## 目标

当前真实通知通道只有微信/iLink。不要实现或假设邮件、Telegram、企业微信、本地声音、桌面提醒等备用通道。

请实现 Hermes 可靠通知队列：

1. 识别 `ret=-2` 或 rate limited 后，不再秒级重试。
2. 写入通知通道状态，例如 `agent-runtime/notifications/state.json`。
3. 限流时设置 `cooldown_until`，任务保持 pending 或 delayed，不移动 done。
4. 通知任务支持优先级：`urgent > high > normal > low`。
5. normal/low 通知合并为摘要，减少触发限流。
6. urgent 通知优先发送；如果限流，必须在 pending_summary 顶部突出显示“紧急通知尚未送达”。
7. 成功发送后才能移动 done。
8. 当前 failed 中因限流失败的通知要给出恢复方案，能恢复则转回 retry/delayed 队列。
9. 文档中明确：没有备用通道时，本系统只能保证“不丢消息、状态可查、恢复后补发”，不能突破微信平台限流。

## 建议状态格式

```json
{
  "wechat_channel": {
    "status": "ok|rate_limited|degraded",
    "last_success_at": "...",
    "last_failure_at": "...",
    "cooldown_until": "...",
    "last_failure_reason": "rate_limited",
    "send_count_in_window": 0
  }
}
```

## 测试要求

至少覆盖：限流识别、cooldown 写入、cooldown 期间不发送、normal 聚合、urgent 优先、urgent 限流不丢、成功前不 done、敏感配置不入库。

## 输出报告

生成：

```text
reports/codex/implement_hermes_reliable_notification_queue_no_alt_channel_20260527.md
```

报告必须说明：实现内容、测试结果、当前限流失败任务的处理结果、是否生成 Hermes 通知待办、commit hash。

## 安全边界

不连接数据库，不运行 MyDCA-Board 业务脚本，不修改真实账本/账户/持仓/交易，不自动交易，不配置不存在的备用通道，不扩大 L4 自动执行权限。
