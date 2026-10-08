package com.timelordtty.mydca.data.dto

data class DataReadinessDto(
    val scope: String? = null,
    val month: String? = null,
    val checkedAt: String? = null,
    val evidence: List<DataReadinessEvidenceDto> = emptyList()
)

data class DataReadinessEvidenceDto(
    val area: String? = null,
    val state: String? = null,
    val reason: String? = null,
    val source: String? = null,
    val dataTime: String? = null,
    val nextStep: String? = null
)
