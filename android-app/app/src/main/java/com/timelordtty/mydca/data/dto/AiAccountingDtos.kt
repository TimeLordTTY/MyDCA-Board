package com.timelordtty.mydca.data.dto

data class ParseTextRequestDto(
    val text: String,
    val sourceRef: String? = null,
)

data class AccountingIntentDto(
    val sourceType: String? = null,
    val sourceRef: String? = null,
    val rawInput: String? = null,
    val txnType: String? = null,
    val amount: Double? = null,
    val note: String? = null,
    val accountId: Long? = null,
    val accountNameHint: String? = null,
    /** 投资候选（BUY / SUBSCRIPTION）的真实产品 ID；规则解析不会自动匹配，必须由主人选择。 */
    val productId: Long? = null,
    /** 投资候选的产品名称提示，只供复核，不可替代 productId。 */
    val productNameHint: String? = null,
    val targetAccountId: Long? = null,
    val targetAccountNameHint: String? = null,
    val confidence: Double? = null,
    val expectedNavDate: String? = null,
    val expectedConfirmDate: String? = null,
    val missingFields: List<String> = emptyList(),
    val parsedPayloadJson: String? = null,
)

data class DraftFromIntentRequestDto(
    val intent: AccountingIntentDto,
)

data class DraftFromIntentResponseDto(
    val intent: AccountingIntentDto? = null,
    val draft: DraftLedgerEntryDto,
)
