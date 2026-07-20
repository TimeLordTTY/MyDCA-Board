package com.timelordtty.mydca.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 保存通知点击目标，登录完成后仍可继续导航。 */
object NotificationNavigationTarget {
    const val EXTRA_CANDIDATE_ID = "notification_candidate_id"
    private val mutableCandidateId = MutableStateFlow<String?>(null)
    val candidateId: StateFlow<String?> = mutableCandidateId

    fun set(candidateId: String?) {
        if (!candidateId.isNullOrBlank()) mutableCandidateId.value = candidateId
    }

    fun consume(candidateId: String) {
        if (mutableCandidateId.value == candidateId) mutableCandidateId.value = null
    }
}
