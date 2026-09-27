package com.timelordtty.mydca.ocr

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.dto.AccountingIntentDto
import com.timelordtty.mydca.outbox.DraftCreationGateway

/**
 * 只接收人工确认后的文本和候选 intent，类型上不允许图片进入网络请求。
 *
 * 继承 [DraftCreationGateway]：outbox 复用同一个“创建 DRAFT”能力，
 * 因此草稿创建链路只有一份实现，不存在自动 preview / confirm 的旁路入口。
 */
interface OcrDraftGateway : DraftCreationGateway {
    suspend fun parseText(text: String, sourceRef: String): NetworkResult<AccountingIntentDto>
}
