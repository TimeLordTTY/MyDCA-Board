package com.timelordtty.dca.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Stateless owner-input scenarios; allocations are never posted or reserved. */
public final class MultiGoalScenarioDTO {
    private MultiGoalScenarioDTO() {}
    public record Allocation(String goalId, BigDecimal monthlyAmount) {}
    public record Input(String name, GoalForecastDTO.Request cashflow, List<Allocation> allocations) {}
    public record Request(List<Input> scenarios) {}
    public record BudgetMonth(String month, BigDecimal sharedUpperBound, BigDecimal specifiedTotal,
                              String allocationStatus, Boolean overLimit) {}
    public record GoalResult(String goalId, LocalDate targetDate, String allocationStatus,
                             GoalForecastDTO.Forecast forecast, String deadlineStatus) {}
    public record Scenario(String name, Input assumptions, List<BudgetMonth> months,
                           List<GoalResult> goals, List<String> warnings) {}
    public record Change(String field, Object before, Object after) {}
    public record Result(Instant readStartedAt, Instant readCompletedAt, String source,
                         List<Scenario> scenarios, List<Change> changes) {}
}
