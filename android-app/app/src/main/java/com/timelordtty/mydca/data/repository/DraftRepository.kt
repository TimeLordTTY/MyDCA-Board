package com.timelordtty.mydca.data.repository

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.api.WealthHubApi
import com.timelordtty.mydca.data.dto.DraftLedgerEntryDto
import com.timelordtty.mydca.data.dto.DraftPreviewDto
import com.timelordtty.mydca.data.dto.TodayTodoDto

/**
 * 草稿与待办数据仓库。
 *
 * 首版用于隔离 API 调用和 UI 状态，后续可在这里加入登录态、缓存和重试策略。
 */
class DraftRepository(
    private val api: WealthHubApi,
) {
    suspend fun getTodayTodos(): NetworkResult<TodayTodoDto> = safeCall {
        api.getTodayTodos()
    }

    suspend fun listDrafts(): NetworkResult<List<DraftLedgerEntryDto>> = safeCall {
        api.listDrafts(status = "DRAFT")
    }

    suspend fun previewDraft(draftId: Long): NetworkResult<DraftPreviewDto> = safeCall {
        api.previewDraft(draftId)
    }

    suspend fun confirmDraft(draftId: Long): NetworkResult<DraftLedgerEntryDto> = safeCall {
        api.confirmDraft(draftId)
    }

    private suspend fun <T> safeCall(block: suspend () -> T): NetworkResult<T> {
        return try {
            NetworkResult.Success(block())
        } catch (error: Throwable) {
            NetworkResult.Failure(error.message ?: "移动端接口调用失败", error)
        }
    }
}
