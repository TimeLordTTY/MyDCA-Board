package com.timelordtty.mydca.data.repository

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.api.WealthHubApi
import com.timelordtty.mydca.data.dto.AccountingIntentDto
import com.timelordtty.mydca.data.dto.DraftFromIntentRequestDto
import com.timelordtty.mydca.data.dto.DraftFromIntentResponseDto
import com.timelordtty.mydca.data.dto.ParseTextRequestDto
import com.timelordtty.mydca.ocr.OcrDraftGateway

class AiAccountingRepository(
    private val api: WealthHubApi,
) : OcrDraftGateway {
    override suspend fun parseText(text: String, sourceRef: String): NetworkResult<AccountingIntentDto> = safeCall {
        api.parseAccountingText(ParseTextRequestDto(text = text, sourceRef = sourceRef))
    }

    suspend fun draftFromIntent(intent: AccountingIntentDto): NetworkResult<DraftFromIntentResponseDto> = safeCall {
        api.draftFromIntent(DraftFromIntentRequestDto(intent = intent))
    }

    override suspend fun createDraft(intent: AccountingIntentDto): NetworkResult<DraftFromIntentResponseDto> {
        return draftFromIntent(intent)
    }

    private suspend fun <T> safeCall(block: suspend () -> T): NetworkResult<T> {
        return try {
            NetworkResult.Success(block())
        } catch (error: Throwable) {
            NetworkResult.Failure(error.message ?: "Android AI 记账接口调用失败", error)
        }
    }
}
