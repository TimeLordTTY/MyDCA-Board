package com.timelordtty.dca.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** User-defined observation metadata. All ratios are fractions: 0.1 means 10%. */
public final class AllocationPolicyDTO {
    private AllocationPolicyDTO() {}
    public enum Status { IN_RANGE, BELOW_BAND, ABOVE_BAND, TAKE_PROFIT_WATCH, UNKNOWN }
    public record Config(String scope, Long productId, String assetType, BigDecimal target,
                         BigDecimal lowerBound, BigDecimal upperBound, BigDecimal returnThreshold,
                         List<BigDecimal> takeProfitThresholds, Boolean enabled, String note) {}
    public record Policy(String id, Config config, Instant createdAt, String disclaimer) {}
    public record Evaluation(String policyId, Status status, String reason, BigDecimal allocation,
                             BigDecimal returnRate, List<BigDecimal> reachedTakeProfitThresholds,
                             Instant evaluatedAt, String disclaimer) {}
}
