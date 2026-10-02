package com.timelordtty.dca.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Goal metadata and read-only progress; completionRate is a fraction, not a percent. */
public final class GoalTrackingDTO {
    private GoalTrackingDTO() {}
    public enum State { ACTIVE, PAUSED, ARCHIVED }
    public enum Measure { TOTAL_ASSETS, CASH, POSITION_VALUE }
    public enum Quality { OK, PARTIAL, UNKNOWN }
    public record Config(String name, BigDecimal targetValue, LocalDate targetDate, String currency,
                         String scope, Measure measure, State state, String note) {}
    public record Goal(String id, Config config, Instant createdAt) {}
    public record Progress(String goalId, Quality quality, String reason, BigDecimal currentValue,
                           BigDecimal knownValue, BigDecimal completionRate, Boolean completed,
                           LocalDate asOfDate, long daysRemaining, boolean overdue) {}
}
