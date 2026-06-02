package com.timelordtty.dca.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class LedgerStatsQueryDTO {
    private Long userId;
    private Long familyId;
    private String scope = "PERSONAL";
    private LocalDate startDate;
    private LocalDate endDate;
    private List<String> txnTypes;
    private List<Long> accountIds;
    private List<Long> parentAccountIds;
    private List<Long> effectiveAccountIds;
    private List<Long> categoryIds;
    private String categoryL1;
    private String categoryL2;
    private List<Long> productIds;
    private Boolean includeTransfer = false;
    private String period = "MONTH";
    private String groupBy = "CATEGORY";
    private Integer limit = 10;
}
