package com.timelordtty.dca.dto.mobile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class MobileCashFlowDto {
    private LocalDate monthStart;
    private LocalDate monthEnd;
    private BigDecimal income = BigDecimal.ZERO;
    private BigDecimal expense = BigDecimal.ZERO;
    private BigDecimal netCashFlow = BigDecimal.ZERO;
    private BigDecimal investmentInflow = BigDecimal.ZERO;
    private BigDecimal investmentOutflow = BigDecimal.ZERO;
    private List<MobileActivityItemDto> recentActivities;

    public LocalDate getMonthStart() { return monthStart; }
    public void setMonthStart(LocalDate monthStart) { this.monthStart = monthStart; }
    public LocalDate getMonthEnd() { return monthEnd; }
    public void setMonthEnd(LocalDate monthEnd) { this.monthEnd = monthEnd; }
    public BigDecimal getIncome() { return income; }
    public void setIncome(BigDecimal income) { this.income = income; }
    public BigDecimal getExpense() { return expense; }
    public void setExpense(BigDecimal expense) { this.expense = expense; }
    public BigDecimal getNetCashFlow() { return netCashFlow; }
    public void setNetCashFlow(BigDecimal netCashFlow) { this.netCashFlow = netCashFlow; }
    public BigDecimal getInvestmentInflow() { return investmentInflow; }
    public void setInvestmentInflow(BigDecimal investmentInflow) { this.investmentInflow = investmentInflow; }
    public BigDecimal getInvestmentOutflow() { return investmentOutflow; }
    public void setInvestmentOutflow(BigDecimal investmentOutflow) { this.investmentOutflow = investmentOutflow; }
    public List<MobileActivityItemDto> getRecentActivities() { return recentActivities; }
    public void setRecentActivities(List<MobileActivityItemDto> recentActivities) { this.recentActivities = recentActivities; }
}
