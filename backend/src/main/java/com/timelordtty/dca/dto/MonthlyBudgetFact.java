package com.timelordtty.dca.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Read-only income/expense posting evidence; a missing posting is retained by LEFT JOIN. */
@Data
public class MonthlyBudgetFact {
    private String txnId;
    private Long categoryId;
    private LocalDate tradeDate;
    private String accountType;
    private String postingType;
    private BigDecimal amount;
    private String currency;
}
