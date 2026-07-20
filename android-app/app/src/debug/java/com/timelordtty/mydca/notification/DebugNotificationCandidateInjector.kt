package com.timelordtty.mydca.notification

/** 仅 debug 构建可调用的 TEST 候选注入器，不触发网络或正式入账。 */
object DebugNotificationCandidateInjector {
    fun inject(now: Long = System.currentTimeMillis()): Boolean {
        val id = "test-${now / 60_000L}"
        return NotificationCandidateStore.add(
            NotificationCandidate(
                id = id,
                fingerprint = id,
                packageName = "com.tencent.mm",
                appLabel = "TEST 微信支付",
                postedAt = now,
                titleSnippet = "TEST 支付通知",
                textSnippet = "TEST 候选，仅用于本地验收",
                amount = "0.01",
                sourceHint = "TEST",
            ),
        )
    }
}
