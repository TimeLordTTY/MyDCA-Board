package com.timelordtty.mydca.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * 通知候选内存队列。
 * 仅保留最近少量事件，不落库、不写文件、不跨进程持久化完整通知原文。
 */
object NotificationCandidateStore {
    private const val MAX_RECENT_CANDIDATES = 20
    private val mutableCandidates = MutableStateFlow<List<NotificationCandidate>>(emptyList())

    val candidates: StateFlow<List<NotificationCandidate>> = mutableCandidates

    fun add(candidate: NotificationCandidate) {
        mutableCandidates.update { current ->
            (listOf(candidate) + current)
                .distinctBy { it.id }
                .take(MAX_RECENT_CANDIDATES)
        }
    }

    fun clear() {
        mutableCandidates.value = emptyList()
    }
}
