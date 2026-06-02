package com.timelordtty.dca.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LedgerStatsTrendDTO {
    private String period;
    private BigDecimal income = BigDecimal.ZERO;
    private BigDecimal expense = BigDecimal.ZERO;
    private BigDecimal netCashflow = BigDecimal.ZERO;
    private BigDecimal investmentInflow = BigDecimal.ZERO;
    private BigDecimal investmentOutflow = BigDecimal.ZERO;
    private Integer txnCount = 0;
}
