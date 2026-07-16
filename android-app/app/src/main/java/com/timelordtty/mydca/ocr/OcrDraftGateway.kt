package com.timelordtty.mydca.ocr

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.dto.AccountingIntentDto
import com.timelordtty.mydca.data.dto.DraftFromIntentResponseDto

/** 只接收人工确认后的文本和候选 intent，类型上不允许图片进入网络请求。 */
interface OcrDraftGateway {
    suspend fun parseText(text: String, sourceRef: String): NetworkResult<AccountingIntentDto>
    suspend fun createDraft(intent: AccountingIntentDto): NetworkResult<DraftFromIntentResponseDto>
}
