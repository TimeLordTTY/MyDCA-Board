package com.timelordtty.mydca.outbox

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.dto.AccountingIntentDto
import com.timelordtty.mydca.data.dto.DraftFromIntentResponseDto

/**
 * Outbox 唯一允许依赖的网络边界：只创建 DRAFT。
 * 类型上不存在 preview / confirm / ignore / 正式入账入口，所以重试不可能越过人工确认边界。
 */
interface DraftCreationGateway {
    suspend fun createDraft(intent: AccountingIntentDto): NetworkResult<DraftFromIntentResponseDto>
}
