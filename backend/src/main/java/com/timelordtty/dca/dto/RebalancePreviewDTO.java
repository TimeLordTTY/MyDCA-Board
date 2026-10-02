package com.timelordtty.dca.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Signed amounts describe hypothetical transfers within a fixed portfolio, never orders. */
public record RebalancePreviewDTO(String policyId, String status, String description,
        BigDecimal totalAssets, BigDecimal currentWeight, BigDecimal targetWeight,
        BigDecimal lowerBound, BigDecimal upperBound, BigDecimal deviationPercentagePoints,
        Scenario targetScenario, Scenario bandScenario, LocalDate dataDate,
        List<PriceEvidence> prices, List<FinanceRadarDTO.Warning> warnings) {
    public record Scenario(BigDecimal selectedAdjustment, BigDecimal remainderAdjustment) {}
    public record PriceEvidence(Long productId, String status, LocalDate priceDate,
                                LocalDate valuationDate, String priceSource) {}
}
