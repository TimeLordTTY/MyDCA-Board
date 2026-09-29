package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.dto.FinanceRadarDto
import com.timelordtty.mydca.data.repository.FinanceRadarRepository

data class FinanceRadarState(
    val snapshot: FinanceRadarDto? = null,
    val lastSuccessAt: Long? = null,
    val loading: Boolean = false,
    val error: String? = null,
) {
    // State is intentionally not persisted. A restored process must fetch again.
    val isStale: Boolean get() = snapshot != null && (loading || error != null)
}

class FinanceRadarStateHolder(
    private val repository: FinanceRadarRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun refresh(previous: FinanceRadarState): FinanceRadarState =
        when (val result = repository.load()) {
            is NetworkResult.Success -> FinanceRadarState(
                snapshot = result.data,
                lastSuccessAt = now(),
            )
            is NetworkResult.Failure -> previous.copy(loading = false, error = result.message)
        }
}
