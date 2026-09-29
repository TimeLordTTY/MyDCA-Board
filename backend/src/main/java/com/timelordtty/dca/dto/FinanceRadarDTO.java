package com.timelordtty.dca.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Read-only facts. Null amounts/counts mean that the server cannot establish a complete value. */
public record FinanceRadarDTO(LocalDate date, String scope, Assets assets, Counts counts,
                              List<MarketFact> markets, List<Warning> warnings) {
    public record Assets(String status, BigDecimal cashBalance, BigDecimal investmentCost,
                         BigDecimal positionValue, BigDecimal liabilities, BigDecimal totalAssets,
                         BigDecimal netWorth) {}

    public record Counts(int drafts, Integer outbox, Integer pendingOrders, Integer awaitingSettlement,
                         Integer reconciliationWarning, Integer reconciliationBroken) {}

    public record MarketFact(Long productId, String status, LocalDate priceDate, LocalDate valuationDate,
                             String indicatorStatus, LocalDate indicatorDate) {}

    public record Warning(String code, String status, String message) {}
}
