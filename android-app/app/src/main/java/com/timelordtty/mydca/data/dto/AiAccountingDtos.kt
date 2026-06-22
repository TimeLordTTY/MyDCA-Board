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
    val confidence: Double? = null,
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
