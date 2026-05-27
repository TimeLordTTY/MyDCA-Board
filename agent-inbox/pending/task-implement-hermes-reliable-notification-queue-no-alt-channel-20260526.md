---
task_id: task-implement-hermes-reliable-notification-queue-no-alt-channel-20260526
title: 实现 Hermes 单通道可靠通知队列：限流感知、聚合、优先级、延迟重试与紧急状态保全
target_agent: codex
task_type: engineering_change
risk_level: high
auto_level: L4_CONFIRM_REQUIRED
requires_model: false
requires_confirmation: true
owner_confirmation_context: 主人已明确指出：当前没有可用备用通知通道，邮件、Telegram、企业微信、本地桌面/声音都不可依赖；不能靠等待冷却解决紧急任务通知问题。需要在微信/iLink 单通道前提下实现可靠通知队列：限流感知、聚合、优先级、延迟重试、紧急通知保全、状态可追踪，并避免因短时间多次发送触发 ret=-2 限流。
model_tier: none
created_by: chatgpt-coordinator
created_at: 2026-05-26T23:45:00+08:00
allowed_paths:
  - AGENTS.md
  - CURRENT_STATE.md
  - docs/
  - agent-runtime/hermes/
  - agent-runtime/deploy/
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
  - expose_sensitive_env
  - configure_nonexistent_alt_channel
---

# 实现 Hermes 单通道可靠通知队列

**作者**：ChatGPT  
**创建时间**：2026-05-26 23:45  
**适用范围**：Hermes pending worker、微信/iLink live send、Codex→Hermes 通知任务、通知限流处理、紧急状态保全。

## 修订记录

| 序号 | 当前作者 | 修改时间 | 修改原因 | 修改内容概要 |
|---|---|---|---|---|
| 1 | ChatGPT | 2026-05-26 23:45 | 主人指出当前没有备用通知通道，不能依赖等待冷却；紧急任务必须有可靠状态保全与限流下的可恢复通知机制 | 新增任务，要求 Codex 实现单微信通道可靠通知队列：限流感知、聚合摘要、优先级、延迟重试、紧急状态保全、冷启动补发与可追踪报告 |

## 1. 背景

当前 Hermes live send 失败已定位为：

```text
iLink sendmessage rate limited: ret=-2
```

这说明微信/iLink 通道存在限流。主人明确指出：

- 当前没有可用备用通道；
- 邮件、Telegram、企业微信均不存在；
- 本地桌面通知/声音不可靠，因为主人电脑可能关闭；
- 不能靠“等冷却”作为解决方案；
- 如果紧急任务已经执行完成，但 Hermes 因限流未通知主人，这是危险状态。

因此本任务目标不是“绕过平台限流”，而是在**只有微信/iLink 一个真实通知通道**的前提下，设计并实现可靠通知队列，保证：

```text
消息不丢
状态可查
限流不无限重试
紧急消息不被低优先级消息挤占
恢复后自动补发
成功前不假装已通知
```

## 2. 核心原则

### 2.1 不伪造成功

只有 live send 返回成功，任务才能进入 `done`。

限流、失败、未发送的任务不得标记为已通知。

### 2.2 单通道可靠，而不是备用通道幻想

当前不要实现邮件、Telegram、企业微信、桌面通知、声音提醒等不存在或不可依赖的通道。

可以在文档中保留“未来备用通道接口”，但默认禁用，不写任何配置，不要求主人现在配置。

### 2.3 限流时保护通道

一旦识别到 `ret=-2` 或 rate limited，不要继续秒级重试。

应进入限流冷却状态，例如：

```text
notification_channel_state = rate_limited
cooldown_until = now + 10~30 minutes
```

冷却期内不再尝试 live send，只维护 pending/delayed 状态。

### 2.4 紧急任务优先

通知必须有优先级：

```text
urgent > high > normal > low
```

低优先级通知不得占用紧急通知发送额度。

### 2.5 聚合减少发送次数

普通完成通知不应每个任务单独发微信。应支持按时间窗口聚合，例如：

```text
5~10 分钟内多个 normal/low 通知合并为一条摘要
```

紧急通知可单独发送，但也要遵守全局限流状态。

## 3. 需要新增或支持的字段

Hermes 通知任务 front matter 增加：

```yaml
notification_priority: urgent|high|normal|low
notification_category: task_completed|task_failed|needs_confirmation|rate_limit_warning|system_health
collapse_key: optional-string
not_before: 2026-05-26T...
expires_at: 2026-05-27T...
max_send_attempts: 3
```

默认规则：

- 未指定 `notification_priority` 时默认为 `normal`；
- 未指定 `max_send_attempts` 时默认为 3；
- `urgent` 也不能无限秒级重试；
- `low` 可以只进入摘要，不单独发送。

## 4. 通知队列状态

请新增或规范一个状态文件，例如：

```text
agent-runtime/notifications/state.json
```

建议记录：

```json
{
  "wechat_channel": {
    "status": "ok|rate_limited|degraded",
    "last_success_at": "...",
    "last_failure_at": "...",
    "cooldown_until": "...",
    "last_failure_reason": "hermes_send_rate_limited_exit_1",
    "send_budget_window_started_at": "...",
    "send_count_in_window": 0
  }
}
```

不得记录真实 target、token、cookie、账号标识或密钥路径。

## 5. 发送预算与限流策略

建议实现保守预算：

```text
normal_send_budget: 每 10 分钟最多 1 条聚合通知
urgent_send_budget: 每 10 分钟最多 1 条紧急通知
```

如果 `ret=-2`：

- 立刻停止本轮剩余发送；
- channel state 标记为 `rate_limited`；
- 设置 `cooldown_until`；
- 当前任务状态为 `delayed_due_to_rate_limit`，不要移动 done；
- 写 reports/hermes；
- 更新 pending_summary；
- 下次 timer 到期后如果 cooldown 已过，再按优先级尝试。

## 6. 聚合摘要规则

普通通知可以聚合为一条：

```text
【AI-Core 摘要】过去 10 分钟有 N 条任务更新
1. [完成] task-a：报告 reports/...
2. [完成] task-b：报告 reports/...
3. [失败] task-c：需要查看 reports/...
```

要求：

- 聚合摘要不超过合理长度，例如 500 字；
- 超过长度则截断，并提示查看 `agent-outbox/hermes/pending_summary.md`；
- 发送成功后，被聚合的任务才能移动 done；
- 若发送失败，被聚合任务仍保留 pending/delayed。

## 7. 紧急任务规则

对 `notification_priority=urgent`：

- 优先于 normal/low；
- 不与 low 聚合；
- 如果通道正常，尽快发送；
- 如果通道限流，写入 `reports/hermes/urgent_notification_delayed_*.md`；
- pending_summary 顶部必须显示“存在未送达紧急通知”；
- 不伪造完成。

由于没有备用通道，紧急通知无法突破微信/iLink 平台限流。系统必须明确呈现：

```text
紧急通知已生成，但微信通道限流，尚未送达。
```

## 8. 冷启动补发

当 Hermes pending worker 下一次运行时：

1. 先读取 channel state；
2. 如果 cooldown 未过，不发送，只更新 summary；
3. 如果 cooldown 已过，优先发送 urgent/high；
4. 再发送 normal 聚合摘要；
5. low 进入摘要或日报，不单独发送。

## 9. 对当前 failed 任务的处理

当前已有因限流失败的通知任务，例如：

```text
hermes-notify-codex-hermes-notification-flow-completed-20260526-230626
hermes-notify-governance-rule-created-20260526-213800
hermes-notify-hermes-live-send-retry-repair-completed-20260526-230626
```

请不要直接丢弃。需要迁移为：

```text
retry_pending 或 delayed_due_to_rate_limit
```

并在 pending_summary 中说明：

```text
这些通知尚未送达，等待限流解除后聚合补发。
```

如果它们已经被移动到 failed，请提供恢复脚本或手工恢复步骤。

## 10. 测试要求

至少覆盖：

1. ret=-2 限流识别；
2. 限流后不再秒级重试；
3. cooldown 状态写入；
4. cooldown 期间任务保持 pending/delayed；
5. cooldown 过后恢复发送；
6. normal/low 聚合摘要；
7. urgent 优先处理；
8. urgent 在限流时不丢失，pending_summary 顶部突出显示；
9. 成功前不移动 done；
10. 不写入任何敏感值。

## 11. 文档同步

更新：

```text
AGENTS.md
CURRENT_STATE.md
docs/ai_core_document_git_governance_rules.md 或新增 docs/hermes_reliable_notification_queue.md
```

必须说明：

- 当前真实通知通道只有微信/iLink；
- 不存在备用通道时，不应幻想本地桌面/声音能覆盖服务器到主人；
- 可靠性依靠队列、优先级、聚合、延迟重试、状态保全，而不是无限重试；
- 紧急通知在平台限流时无法保证即时送达，但系统必须保留并突出显示未送达状态。

## 12. 输出报告

生成报告：

```text
reports/codex/implement_hermes_reliable_notification_queue_no_alt_channel_20260526.md
```

报告必须包含：

- 当前限流根因；
- 为什么不采用邮件/Telegram/企业微信/本地通知；
- 新队列策略；
- 状态文件格式；
- 聚合与优先级规则；
- 当前 failed 通知的恢复方案；
- 测试结果；
- 是否生成 Hermes 通知待办；
- commit hash；
- 安全边界。

## 13. 安全边界

- 不连接数据库；
- 不运行 MyDCA-Board 业务脚本；
- 不修改真实账本、账户余额、持仓成本或交易记录；
- 不自动交易；
- 不提交微信目标、token、cookie、API key、数据库密码或本地密钥路径；
- 不配置不存在的备用通道；
- 不让 Hermes 执行工程修改；
- 不让普通后台 worker 执行 codex exec；
- 不扩大 L4 自动执行权限。
