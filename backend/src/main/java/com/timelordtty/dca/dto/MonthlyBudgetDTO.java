package com.timelordtty.dca.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class MonthlyBudgetDTO {
    private MonthlyBudgetDTO() {}
    public enum Kind { INCOME, FIXED_EXPENSE, FLEXIBLE_EXPENSE, RESERVE }
    public enum Quality { OK, PARTIAL, UNKNOWN }
    public record Item(String name, Kind kind, Long categoryId, BigDecimal planned) {}
    public record Config(String name, String month, String currency, String scope, List<Item> items) {}
    public record Budget(String id, Config config, Instant createdAt) {}
    public record ItemComparison(Item item, Quality quality, BigDecimal actual, BigDecimal knownActual,
                                 BigDecimal remaining, Boolean overspent) {}
    public record Comparison(String budgetId, Quality quality, BigDecimal plannedIncome,
                             BigDecimal plannedExpenses, BigDecimal plannedReserve, BigDecimal plannedSurplus,
                             BigDecimal actualIncome, BigDecimal actualExpenses, BigDecimal actualSurplus,
                             BigDecimal remainingBudget, Boolean overspent, int unmatchedPostings,
                             List<ItemComparison> items, List<String> warnings) {}
}
