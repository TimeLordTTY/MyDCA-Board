package com.timelordtty.dca.dto;

import com.timelordtty.dca.model.LedgerPosting;
import com.timelordtty.dca.model.OrderFundingLine;
import com.timelordtty.dca.model.SettlementConfirm;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/** Read-only facts and reconciliation for one completed manual settlement. */
@Data
public class SettlementAuditDTO {
    private String orderId;
    private String orderType;
    private String orderStatus;
    private Long productId;
    private String productName;
    private SettlementConfirm settlement;
    private List<OrderFundingLine> fundingLines;
    private String ledgerTxnId;
    private List<LedgerPosting> postings;
    private BigDecimal cashDelta;
    private BigDecimal positionSharesDelta;
    private BigDecimal feeAmount;
    private String reconciliationStatus;
    private List<String> reasons;
}
