package com.timelordtty.dca.dto.mobile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class MobileWealthOverviewDto {
    private BigDecimal totalAssets;
    private BigDecimal cashBalance;
    private BigDecimal positionValue;
    private BigDecimal totalLiabilities;
    private BigDecimal netWorth;
    private int accountCount;
    private BigDecimal spendableAmount = BigDecimal.ZERO;
    private BigDecimal reservedFundAmount = BigDecimal.ZERO;
    private BigDecimal investableAmount = BigDecimal.ZERO;
    private BigDecimal unallocatedAmount = BigDecimal.ZERO;
    private int draftCount;
    private int settlementCount;
    private int suggestionCount;
    private int totalTodoCount;
    private LocalDateTime lastUpdatedAt;
    private List<MobileActivityItemDto> recentActivities;

    public BigDecimal getTotalAssets() {
        return totalAssets;
    }

    public void setTotalAssets(BigDecimal totalAssets) {
        this.totalAssets = totalAssets;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public void setCashBalance(BigDecimal cashBalance) {
        this.cashBalance = cashBalance;
    }

    public BigDecimal getPositionValue() {
        return positionValue;
    }

    public void setPositionValue(BigDecimal positionValue) {
        this.positionValue = positionValue;
    }

    public BigDecimal getTotalLiabilities() {
        return totalLiabilities;
    }

    public void setTotalLiabilities(BigDecimal totalLiabilities) {
        this.totalLiabilities = totalLiabilities;
    }

    public BigDecimal getNetWorth() {
        return netWorth;
    }

    public void setNetWorth(BigDecimal netWorth) {
        this.netWorth = netWorth;
    }

    public int getAccountCount() {
        return accountCount;
    }

    public void setAccountCount(int accountCount) {
        this.accountCount = accountCount;
    }

    public BigDecimal getSpendableAmount() { return spendableAmount; }
    public void setSpendableAmount(BigDecimal spendableAmount) { this.spendableAmount = spendableAmount; }
    public BigDecimal getReservedFundAmount() { return reservedFundAmount; }
    public void setReservedFundAmount(BigDecimal reservedFundAmount) { this.reservedFundAmount = reservedFundAmount; }
    public BigDecimal getInvestableAmount() { return investableAmount; }
    public void setInvestableAmount(BigDecimal investableAmount) { this.investableAmount = investableAmount; }
    public BigDecimal getUnallocatedAmount() { return unallocatedAmount; }
    public void setUnallocatedAmount(BigDecimal unallocatedAmount) { this.unallocatedAmount = unallocatedAmount; }

    public int getDraftCount() {
        return draftCount;
    }

    public void setDraftCount(int draftCount) {
        this.draftCount = draftCount;
    }

    public int getSettlementCount() {
        return settlementCount;
    }

    public void setSettlementCount(int settlementCount) {
        this.settlementCount = settlementCount;
    }

    public int getSuggestionCount() {
        return suggestionCount;
    }

    public void setSuggestionCount(int suggestionCount) {
        this.suggestionCount = suggestionCount;
    }

    public int getTotalTodoCount() {
        return totalTodoCount;
    }

    public void setTotalTodoCount(int totalTodoCount) {
        this.totalTodoCount = totalTodoCount;
    }

    public LocalDateTime getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(LocalDateTime lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public List<MobileActivityItemDto> getRecentActivities() {
        return recentActivities;
    }

    public void setRecentActivities(List<MobileActivityItemDto> recentActivities) {
        this.recentActivities = recentActivities;
    }
}
