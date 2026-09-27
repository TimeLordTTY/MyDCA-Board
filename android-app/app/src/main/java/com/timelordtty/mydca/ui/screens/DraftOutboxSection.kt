package com.timelordtty.mydca.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.timelordtty.mydca.outbox.DraftCreationGateway
import com.timelordtty.mydca.outbox.DraftOutboxEntry
import com.timelordtty.mydca.outbox.DraftOutboxOutcome
import com.timelordtty.mydca.outbox.DraftOutboxQueue
import com.timelordtty.mydca.outbox.DraftOutboxStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 本地待重试草稿队列。
 *
 * 只展示待重试数量、来源、创建时间、最近失败原因和重试 / 丢弃操作；
 * 不默认展示完整敏感原文，也不提供任何 preview / confirm 入口。
 */
@Composable
fun DraftOutboxSection(
    outbox: DraftOutboxQueue?,
    gateway: DraftCreationGateway?,
    onOpenDraft: (Long) -> Unit,
    onDraftCreated: () -> Unit,
    highlighted: Boolean = false,
) {
    if (outbox == null) return
    val entries by outbox.entries.collectAsState()
    val outcome by outbox.lastOutcome.collectAsState()
    val lastRunMessage by outbox.lastRunMessage.collectAsState()
    val scope = rememberCoroutineScope()
    var busyEntryId by remember { mutableStateOf<String?>(null) }
    var retryingAll by remember { mutableStateOf(false) }
    val createGateway = gateway

    SectionCard(
        title = "本地待重试草稿（${entries.size}）",
        description = "创建 DRAFT 失败但未丢失的采集会加密暂存在这里，跨重启保留。重试只会重新创建 DRAFT，不会自动 preview、confirm 或正式入账。",
    ) {
        Text("待重试数量：${entries.size}")
        if (highlighted) {
            StatusPill("已定位到你刚才打开的待重试区域")
        }
        if (entries.isEmpty()) {
            StatusPill("暂无待重试草稿")
        }
        entries.forEach { entry ->
            DraftOutboxEntryCard(
                entry = entry,
                busy = retryingAll || busyEntryId == entry.id,
                retryEnabled = createGateway != null,
                onRetry = {
                    createGateway?.let { ready ->
                        busyEntryId = entry.id
                        scope.launch {
                            outbox.retryEntry(entry.id, ready)
                            busyEntryId = null
                            onDraftCreated()
                        }
                    }
                },
                onDiscard = {
                    outbox.discard(entry.id)
                    onDraftCreated()
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                enabled = entries.isNotEmpty() && !retryingAll && createGateway != null,
                onClick = {
                    createGateway?.let { ready ->
                        retryingAll = true
                        scope.launch {
                            outbox.retryDueEntries(ready)
                            retryingAll = false
                            onDraftCreated()
                        }
                    }
                },
            ) {
                Text(if (retryingAll) "重试中" else "立即重试全部")
            }
        }
        lastRunMessage?.let { Text(it) }
        outcome?.let { result -> DraftOutboxOutcomeCard(result, onOpenDraft) }
    }
}

@Composable
private fun DraftOutboxEntryCard(
    entry: DraftOutboxEntry,
    busy: Boolean,
    retryEnabled: Boolean,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
) {
    SectionCard(
        title = "${entry.origin.label} · ${formatOutboxTime(entry.createdAt)}",
        description = entry.summary.ifBlank { "仅保存脱敏摘要" },
    ) {
        KeyValueRow("来源类型", entry.sourceType)
        KeyValueRow("队列状态", entry.status.label)
        KeyValueRow("重试次数", entry.retryCount.toString())
        KeyValueRow(
            "下次自动重试",
            if (entry.status == DraftOutboxStatus.PENDING) {
                formatOutboxTime(entry.nextRetryAt)
            } else {
                "不自动重试"
            },
        )
        KeyValueRow("最近失败原因", entry.lastErrorMessage ?: "尚未记录")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(enabled = retryEnabled && !busy, onClick = onRetry) {
                Text(if (busy) "重试中" else "立即重试")
            }
            OutlinedButton(enabled = !busy, onClick = onDiscard) {
                Text("丢弃")
            }
        }
    }
}

@Composable
private fun DraftOutboxOutcomeCard(
    outcome: DraftOutboxOutcome,
    onOpenDraft: (Long) -> Unit,
) {
    SectionCard(
        title = "最近一次重试结果",
        description = if (outcome.canOpenDraft) {
            "${outcome.origin.label} 的草稿已创建：DRAFT #${outcome.draftId}。仍需你手动 preview 并二次确认。"
        } else {
            "服务端已存在同一来源的草稿 #${outcome.draftId}（状态：${outcome.draftStatus ?: "未知"}）。" +
                "这里只展示状态，不会发起 preview 或 confirm。"
        },
    ) {
        if (outcome.canOpenDraft) {
            Button(onClick = { onOpenDraft(outcome.draftId) }) {
                Text("打开草稿")
            }
        } else {
            StatusPill(outcome.draftStatus ?: "状态未知")
        }
    }
}

private fun formatOutboxTime(millis: Long?): String {
    if (millis == null || millis <= 0L) return "未知"
    return SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(millis))
}
