package com.timelordtty.dca.dto;

import java.time.Instant;
import java.util.List;

/** Evidence only: READY never means a financial target has been met. */
public record DataReadinessDTO(String scope, String month, Instant checkedAt, List<Evidence> evidence) {
    public enum State { READY, PARTIAL, UNAVAILABLE, UNKNOWN }
    public record Evidence(String area, State state, String reason, String source,
                           String dataTime, String nextStep) {}
}
