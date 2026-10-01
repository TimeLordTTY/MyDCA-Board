package com.timelordtty.dca.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Risk metadata only. Ratios use fractions (0.1 means 10%). */
public final class RiskWatchDTO {
    private RiskWatchDTO() {}
    public enum Type { RETURN, DRAWDOWN, ALLOCATION_DEVIATION, STALE, CONCENTRATION, NOTE }
    public enum Severity { INFO, WARNING, CRITICAL }
    public enum EventState { OPEN, ACKNOWLEDGED, MUTED, RESOLVED }
    public record Event(String fingerprint, Snapshot evidence, EventState state, boolean visible,
                        Instant acknowledgedAt, Instant resolvedAt, Instant mutedUntil) {}
    public record MuteRequest(Instant mutedUntil) {}
    public enum Direction { ABOVE, BELOW }
    public record Config(String scope, Type type, Long productId, String assetType,
                         BigDecimal threshold, BigDecimal target, Direction direction,
                         Severity severity, String note, boolean muted) {}
    public record Rule(String id, Config config, Instant createdAt, String disclaimer) {}
    public record Snapshot(String id, String ruleId, String sourceDataTimestamp, String sourceDataHash,
                           String status, boolean matched, Severity severity, String reason,
                           BigDecimal observedValue, BigDecimal threshold, Instant createdAt,
                           String disclaimer) {}
}
