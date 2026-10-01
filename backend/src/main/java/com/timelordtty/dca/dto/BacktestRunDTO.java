package com.timelordtty.dca.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** One request to the offline simulator; result is null for failed runs. */
public record BacktestRunDTO(String historyRunId, Long ownerUserId, Long ownerFamilyId,
        String dataset, String datasetHash, String strategy, String strategyVersion,
        String canonicalParams, String paramsHash, String engineVersion,
        Instant startedAt, Instant finishedAt, String status, boolean cacheHit,
        String failureCode, JsonNode metrics, JsonNode result, String researchPlanId) {
    public BacktestRunDTO(String historyRunId, Long ownerUserId, Long ownerFamilyId,
            String dataset, String datasetHash, String strategy, String strategyVersion,
            String canonicalParams, String paramsHash, String engineVersion,
            Instant startedAt, Instant finishedAt, String status, boolean cacheHit,
            String failureCode, JsonNode metrics, JsonNode result) {
        this(historyRunId, ownerUserId, ownerFamilyId, dataset, datasetHash, strategy, strategyVersion,
                canonicalParams, paramsHash, engineVersion, startedAt, finishedAt, status, cacheHit,
                failureCode, metrics, result, null);
    }
}
