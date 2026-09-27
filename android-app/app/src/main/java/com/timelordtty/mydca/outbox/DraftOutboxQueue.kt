package com.timelordtty.mydca.outbox

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.dto.AccountingIntentDto
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 本地“待创建草稿”队列。
 *
 * 只承载草稿创建链路：队列里唯一的网络动作是 [DraftCreationGateway.createDraft]，
 * 没有 preview / confirm / ignore / QuickEntry / 正式账本入口，所以重试不会也不能越过人工确认边界。
 *
 * 并发控制：批量重试是单实例执行（已在执行时直接跳过），用户手动重试与批量重试共用同一把锁。
 * 退避与次数上限由 [DraftOutboxRetryPolicy] 决定，不会高频无限循环。
 */
class DraftOutboxQueue(
    private val storage: DraftOutboxStorage,
    private val policy: DraftOutboxRetryPolicy = DraftOutboxRetryPolicy(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val onDraftCreated: (DraftOutboxEntry, Long) -> Unit = { _, _ -> },
) {
    private val retryLock = Mutex()
    private val mutableEntries = MutableStateFlow(normalize(storage.read()))
    val entries: StateFlow<List<DraftOutboxEntry>> = mutableEntries.asStateFlow()

    private val mutableLastOutcome = MutableStateFlow<DraftOutboxOutcome?>(null)
    val lastOutcome: StateFlow<DraftOutboxOutcome?> = mutableLastOutcome.asStateFlow()

    private val mutableLastRunMessage = MutableStateFlow<String?>(null)
    val lastRunMessage: StateFlow<String?> = mutableLastRunMessage.asStateFlow()

    /**
     * 入队一条待创建草稿。
     *
     * 只有“创建 DRAFT”这一步失败才会调用这里；同一 sourceType + sourceRef 的采集尝试只保留一条，
     * 重试始终复用同一 sourceRef。缺少稳定 sourceRef 时返回 null，不进入队列。
     */
    @Synchronized
    fun enqueue(
        intent: AccountingIntentDto,
        origin: DraftOutboxOrigin,
        summary: String,
        failure: NetworkResult.Failure,
        now: Long = clock(),
    ): DraftOutboxEntry? {
        val sourceType = intent.sourceType?.takeIf { it.isNotBlank() } ?: return null
        val sourceRef = intent.sourceRef?.takeIf { it.isNotBlank() } ?: return null
        mutableEntries.value
            .firstOrNull { it.sourceType == sourceType && it.sourceRef == sourceRef }
            ?.let { return it }

        val category = DraftOutboxErrorClassifier.classify(failure.cause)
        val entry = DraftOutboxEntry(
            id = idGenerator(),
            sourceType = sourceType,
            sourceRef = sourceRef,
            origin = origin,
            summary = summary.trim().take(SUMMARY_MAX_LENGTH),
            intent = intent,
            createdAt = now,
            retryCount = 0,
            nextRetryAt = nextRetryAt(category, now, failedAttempts = 1),
            lastErrorCategory = category,
            lastErrorMessage = DraftOutboxErrorClassifier.messageFor(category, failure.message),
            lastAttemptAt = now,
            status = statusFor(category, failedAttempts = 0),
        )
        persist(mutableEntries.value + entry)
        return entry
    }

    /** 用户主动丢弃；返回是否真的移除了条目。 */
    @Synchronized
    fun discard(id: String): Boolean {
        val current = mutableEntries.value
        val remaining = current.filterNot { it.id == id }
        if (remaining.size == current.size) return false
        persist(remaining)
        return true
    }

    @Synchronized
    fun clearOutcome() {
        mutableLastOutcome.value = null
        mutableLastRunMessage.value = null
    }

    /** 当前可自动重试的到期条目数，供 UI 展示。 */
    fun dueCount(now: Long = clock()): Int = entries.value.count { policy.isDue(it, now) }

    /** 用户“立即重试”单条；与批量重试互斥，重试成功即出队。 */
    suspend fun retryEntry(id: String, gateway: DraftCreationGateway): Boolean = retryLock.withLock {
        val entry = entries.value.firstOrNull { it.id == id } ?: return@withLock false
        attempt(entry, gateway)
    }

    /**
     * 受控批量重试：单实例执行，已在执行时直接跳过；只处理到期的可重试条目。
     * 返回本次真正尝试过的条目数。
     */
    suspend fun retryDueEntries(gateway: DraftCreationGateway, now: Long = clock()): Int {
        if (!retryLock.tryLock()) return 0
        return try {
            val due = entries.value.filter { policy.isDue(it, now) }
            if (due.isEmpty()) return 0
            var succeeded = 0
            due.forEach { entry ->
                if (attempt(entry, gateway)) succeeded += 1
            }
            mutableLastRunMessage.value = "本次受控重试：尝试 ${due.size} 条，成功 $succeeded 条。"
            due.size
        } finally {
            retryLock.unlock()
        }
    }

    private suspend fun attempt(entry: DraftOutboxEntry, gateway: DraftCreationGateway): Boolean {
        return when (val result = gateway.createDraft(entry.intent)) {
            is NetworkResult.Success -> {
                val draft = result.data.draft
                remove(entry.id)
                onDraftCreated(entry, draft.id)
                mutableLastOutcome.value = DraftOutboxOutcome(
                    entryId = entry.id,
                    origin = entry.origin,
                    draftId = draft.id,
                    draftStatus = draft.status,
                )
                true
            }
            is NetworkResult.Failure -> {
                recordFailure(entry, result, clock())
                false
            }
        }
    }

    @Synchronized
    private fun recordFailure(entry: DraftOutboxEntry, failure: NetworkResult.Failure, now: Long) {
        val failedAttempts = entry.retryCount + 1
        val category = DraftOutboxErrorClassifier.classify(failure.cause)
        val updated = entry.copy(
            retryCount = failedAttempts,
            lastAttemptAt = now,
            lastErrorCategory = category,
            lastErrorMessage = DraftOutboxErrorClassifier.messageFor(category, failure.message),
            nextRetryAt = nextRetryAt(category, now, failedAttempts),
            status = statusFor(category, failedAttempts),
        )
        persist(mutableEntries.value.map { if (it.id == entry.id) updated else it })
    }

    @Synchronized
    private fun remove(id: String) {
        val current = mutableEntries.value
        val remaining = current.filterNot { it.id == id }
        if (remaining.size != current.size) persist(remaining)
    }

    private fun persist(entries: List<DraftOutboxEntry>) {
        val normalized = normalize(entries)
        mutableEntries.value = normalized
        storage.write(normalized)
    }

    private fun nextRetryAt(category: DraftOutboxErrorCategory, now: Long, failedAttempts: Int): Long =
        if (DraftOutboxErrorClassifier.isAutoRetryEligible(category)) {
            policy.nextRetryAt(now, failedAttempts)
        } else {
            now
        }

    private fun statusFor(category: DraftOutboxErrorCategory, failedAttempts: Int): DraftOutboxStatus =
        when (category) {
            DraftOutboxErrorCategory.RETRYABLE ->
                if (policy.isAutoRetryAllowed(failedAttempts)) {
                    DraftOutboxStatus.PENDING
                } else {
                    DraftOutboxStatus.EXHAUSTED
                }
            DraftOutboxErrorCategory.AUTH_REQUIRED -> DraftOutboxStatus.AUTH_PAUSED
            DraftOutboxErrorCategory.BUSINESS,
            DraftOutboxErrorCategory.UNKNOWN,
            -> DraftOutboxStatus.BLOCKED
        }

    private fun normalize(entries: List<DraftOutboxEntry>): List<DraftOutboxEntry> = entries
        .filter { it.sourceType.isNotBlank() && it.sourceRef.isNotBlank() }
        .distinctBy { it.sourceType to it.sourceRef }
        .sortedBy { it.createdAt }

    private companion object {
        const val SUMMARY_MAX_LENGTH = 48
    }
}
