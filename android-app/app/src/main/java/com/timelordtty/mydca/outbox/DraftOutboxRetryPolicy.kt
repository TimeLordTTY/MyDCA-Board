package com.timelordtty.mydca.outbox

/**
 * 有限退避策略：避免高频无限循环。
 * 自动重试只对 RETRYABLE 且到期的条目生效，次数用尽后必须由用户显式重试。
 */
data class DraftOutboxRetryPolicy(
    val maxAutoRetryAttempts: Int = 5,
    val backoffSeconds: List<Long> = listOf(30L, 120L, 600L, 1800L),
) {
    fun isAutoRetryAllowed(failedAttempts: Int): Boolean = failedAttempts < maxAutoRetryAttempts

    /** 第 failedAttempts 次失败之后的下一次自动重试时间；超出退避表时取最长退避。 */
    fun nextRetryAt(now: Long, failedAttempts: Int): Long {
        val index = (failedAttempts - 1).coerceIn(0, backoffSeconds.lastIndex)
        return now + backoffSeconds[index] * 1_000L
    }

    /** 只有等待中的可重试条目才可能在到期后自动重试。 */
    fun isDue(entry: DraftOutboxEntry, now: Long): Boolean =
        entry.status == DraftOutboxStatus.PENDING &&
            isAutoRetryAllowed(entry.retryCount) &&
            now >= entry.nextRetryAt
}
