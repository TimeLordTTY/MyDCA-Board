package com.timelordtty.dca.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.timelordtty.dca.dto.GoalTrackingDTO.Progress;
import com.timelordtty.dca.dto.GoalTrackingDTO.Quality;
import com.timelordtty.dca.dto.MonthlyBudgetDTO.Comparison;

/** All amounts are scenarios; no contribution is posted or reserved. */
public final class GoalForecastDTO {
    private GoalForecastDTO() {}
    public enum Mode { PLANNED, ACTUAL_PLUS_REMAINING }
    public record MonthInput(String month, String budgetId, boolean cashflowCovered) {}
    public record Request(String startMonth, String endMonth, Mode mode, List<MonthInput> months,
                          BigDecimal monthlyExtraSavings, BigDecimal annualRate) {}
    public record Month(String month, String budgetId, Quality quality, String reason,
                        BigDecimal startingProgress, BigDecimal plannedIncome, BigDecimal plannedExpenses,
                        BigDecimal plannedReserve, Comparison actualReading, BigDecimal modeledIncome,
                        BigDecimal modeledExpenses, BigDecimal surplusUpperBound, BigDecimal contribution,
                        BigDecimal mathematicalReturn, BigDecimal cumulativeProgress, BigDecimal remainingGap) {}
    public record Scenario(BigDecimal annualRate, String assumption, Quality quality,
                           String outcome, String achievedMonth, List<Month> months) {}
    public record Forecast(String goalId, String currency, LocalDate asOfDate, Progress actualProgress,
                           Request fixedInputs, Scenario baseline, Scenario mathematicalScenario) {}
}
