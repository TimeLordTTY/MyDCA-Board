package com.timelordtty.dca.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LedgerStatsBreakdownDTO {
    private String key;
    private String name;
    private String groupBy;
    private BigDecimal amount = BigDecimal.ZERO;
    private BigDecimal income = BigDecimal.ZERO;
    private BigDecimal expense = BigDecimal.ZERO;
    private BigDecimal investmentInflow = BigDecimal.ZERO;
    private BigDecimal investmentOutflow = BigDecimal.ZERO;
    private BigDecimal percentage = BigDecimal.ZERO;
    private Integer txnCount = 0;
}
