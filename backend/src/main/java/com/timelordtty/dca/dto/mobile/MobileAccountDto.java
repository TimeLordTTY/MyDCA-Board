package com.timelordtty.dca.dto.mobile;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MobileAccountDto {
    private Long id;
    private Long parentAccountId;
    private String accountName;
    private String parentAccountName;
    private String accountType;
    private String fundUsage;
    private Boolean leaf;
    private Boolean selectableForExpense;
    private String safetyMessage;
    private String currency;
    private BigDecimal balance;
    private BigDecimal reservedAmount;
    private BigDecimal availableAmount;
    private Boolean active;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getParentAccountId() { return parentAccountId; }
    public void setParentAccountId(Long parentAccountId) { this.parentAccountId = parentAccountId; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getParentAccountName() { return parentAccountName; }
    public void setParentAccountName(String parentAccountName) { this.parentAccountName = parentAccountName; }
    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }
    public String getFundUsage() { return fundUsage; }
    public void setFundUsage(String fundUsage) { this.fundUsage = fundUsage; }
    public Boolean getLeaf() { return leaf; }
    public void setLeaf(Boolean leaf) { this.leaf = leaf; }
    public Boolean getSelectableForExpense() { return selectableForExpense; }
    public void setSelectableForExpense(Boolean selectableForExpense) { this.selectableForExpense = selectableForExpense; }
    public String getSafetyMessage() { return safetyMessage; }
    public void setSafetyMessage(String safetyMessage) { this.safetyMessage = safetyMessage; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public BigDecimal getReservedAmount() { return reservedAmount; }
    public void setReservedAmount(BigDecimal reservedAmount) { this.reservedAmount = reservedAmount; }
    public BigDecimal getAvailableAmount() { return availableAmount; }
    public void setAvailableAmount(BigDecimal availableAmount) { this.availableAmount = availableAmount; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
