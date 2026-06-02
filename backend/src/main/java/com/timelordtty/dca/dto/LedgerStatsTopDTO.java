package com.timelordtty.dca.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LedgerStatsTopDTO {
    private String txnId;
    private String txnType;
    private String tradeDate;
    private String note;
    private Long categoryId;
    private Long accountId;
    private String accountName;
    private BigDecimal amount = BigDecimal.ZERO;
}
