package com.timelordtty.mydca.data.dto

data class ResearchPlanDto(
    val id: String,
    val name: String,
    val status: String,
    val strategy: String,
    val strategyVersion: String,
    val description: String? = null,
    val sourceCandidateId: String? = null,
    val sourceRunIds: List<String> = emptyList(),
    val canonicalParamsSnapshot: Map<String, Any?> = emptyMap(),
    val paramsDraft: Map<String, Any?> = emptyMap(),
    val datasetHashes: List<String> = emptyList(),
    val evidenceBundleRef: String? = null,
    val evidenceSnapshot: ResearchEvidenceDto? = null,
    val warnings: List<String> = emptyList(),
)

data class ResearchEvidenceDto(val runs: List<ResearchSourceRunDto> = emptyList())
data class ResearchSourceRunDto(
    val run_id: String,
    val dataset_hash: String? = null,
    val engine_version: String? = null,
    val metrics: Map<String, Double?> = emptyMap(),
)

data class ResearchRunDto(
    val historyRunId: String,
    val status: String,
    val researchPlanId: String? = null,
    val startedAt: String? = null,
    val dataset: String? = null,
    val datasetHash: String? = null,
    val engineVersion: String? = null,
    val failureCode: String? = null,
    val metrics: Map<String, Double?>? = null,
)
