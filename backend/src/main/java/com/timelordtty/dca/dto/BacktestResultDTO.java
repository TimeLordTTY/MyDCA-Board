package com.timelordtty.dca.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Stable, read only JSON contract emitted by the offline backtest engine. */
public record BacktestResultDTO(
        String schema_version,
        String run_id,
        JsonNode provenance,
        JsonNode data_range,
        JsonNode strategy,
        JsonNode metrics,
        JsonNode events,
        JsonNode baseline,
        List<String> warnings,
        String history_run_id,
        Boolean cache_hit
) {}
