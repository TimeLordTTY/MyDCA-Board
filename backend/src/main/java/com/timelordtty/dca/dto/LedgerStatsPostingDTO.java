package com.timelordtty.dca.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class LedgerStatsPostingDTO {
    private String txnId;
    private String txnType;
    private Long userId;
    private Long familyId;
    private Long productId;
    private LocalDateTime requestedAt;
    private LocalDate tradeDate;
    private String status;
    private Long categoryId;
    private Boolean isReimbursable;
    private Boolean isReimbursed;
    private Boolean isReversed;
    private String note;
    private Long postingId;
    private String postingType;
    private Long accountId;
    private String accountName;
    private String accountKind;
    private String accountType;
    private Long parentAccountId;
    private String parentAccountName;
    private BigDecimal amount;
    private BigDecimal shares;
    private String currency;
}
