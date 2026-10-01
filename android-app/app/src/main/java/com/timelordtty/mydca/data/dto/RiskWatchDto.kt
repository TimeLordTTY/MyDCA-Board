package com.timelordtty.mydca.data.dto

data class RiskConfigDto(val scope: String?, val type: String?, val threshold: Double?, val severity: String?, val note: String?, val productId: Long?, val assetType: String?, val target: Double?, val direction: String?, val muted: Boolean = false)
data class RiskRuleDto(val id: String, val config: RiskConfigDto, val createdAt: String?)
data class RiskSnapshotDto(val id: String, val ruleId: String, val sourceDataTimestamp: String?, val status: String?, val matched: Boolean, val severity: String?, val reason: String?, val observedValue: Double?, val threshold: Double?, val createdAt: String?)
data class RiskEventDto(val fingerprint: String, val evidence: RiskSnapshotDto, val state: String?, val visible: Boolean)
