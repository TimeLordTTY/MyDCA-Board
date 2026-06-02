package com.timelordtty.dca.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LedgerStatsSummaryDTO {
    private BigDecimal totalIncome = BigDecimal.ZERO;
    private BigDecimal totalExpense = BigDecimal.ZERO;
    private BigDecimal netCashflow = BigDecimal.ZERO;
    private BigDecimal investmentInflow = BigDecimal.ZERO;
    private BigDecimal investmentOutflow = BigDecimal.ZERO;
    private BigDecimal transferAmount = BigDecimal.ZERO;
    private BigDecimal reimbursableExpense = BigDecimal.ZERO;
    private BigDecimal reimbursedAmount = BigDecimal.ZERO;
    private BigDecimal avgDailyExpense = BigDecimal.ZERO;
    private BigDecimal maxExpenseAmount = BigDecimal.ZERO;
    private Integer txnCount = 0;
    private Integer expenseTxnCount = 0;
    private Integer incomeTxnCount = 0;
}
