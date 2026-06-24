package com.timelordtty.mydca.data.repository

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.api.WealthHubApi
import com.timelordtty.mydca.data.dto.DraftLedgerEntryDto
import com.timelordtty.mydca.data.dto.DraftPreviewDto
import com.timelordtty.mydca.data.dto.IgnoreDraftRequestDto
import com.timelordtty.mydca.data.dto.UpdateDraftRequestDto

/**
 * 草稿数据仓库。
 * 只封装草稿查看、预览、忽略和用户确认接口；不会直接连接数据库或绕过后端安全边界。
 */
class DraftRepository(
    private val api: WealthHubApi,
) {
    suspend fun listDrafts(): NetworkResult<List<DraftLedgerEntryDto>> = safeCall {
        api.listDrafts(status = "DRAFT")
    }

    suspend fun getDraft(draftId: Long): NetworkResult<DraftLedgerEntryDto> = safeCall {
        api.getDraft(draftId)
    }

    suspend fun previewDraft(draftId: Long): NetworkResult<DraftPreviewDto> = safeCall {
        api.previewDraft(draftId)
    }

    suspend fun updateDraft(draftId: Long, request: UpdateDraftRequestDto): NetworkResult<DraftLedgerEntryDto> = safeCall {
        api.updateDraft(draftId, request)
    }

    suspend fun confirmDraft(draftId: Long): NetworkResult<DraftLedgerEntryDto> = safeCall {
        api.confirmDraft(draftId)
    }

    suspend fun ignoreDraft(draftId: Long, reason: String? = null): NetworkResult<DraftLedgerEntryDto> = safeCall {
        api.ignoreDraft(draftId, IgnoreDraftRequestDto(ignoreReason = reason))
    }

    private suspend fun <T> safeCall(block: suspend () -> T): NetworkResult<T> {
        return try {
            NetworkResult.Success(block())
        } catch (error: Throwable) {
            NetworkResult.Failure(error.message ?: "移动端接口调用失败", error)
        }
    }
}
