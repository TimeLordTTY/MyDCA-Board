package com.timelordtty.dca.service;

import com.timelordtty.dca.dto.SettlementAuditDTO;
import com.timelordtty.dca.mapper.*;
import com.timelordtty.dca.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** All queries are scoped to the authenticated order owner. No write mapper is used. */
@Service
public class SettlementAuditService {
    private final OrderMapper orders;
    private final SettlementConfirmMapper settlements;
    private final OrderFundingLineMapper funding;
    private final LedgerTxnMapper txns;
    private final LedgerPostingMapper postings;
    private final ProductMasterMapper products;
    private final AccountMapper accounts;
    private final UserService users;

    public SettlementAuditService(OrderMapper orders, SettlementConfirmMapper settlements,
            OrderFundingLineMapper funding, LedgerTxnMapper txns, LedgerPostingMapper postings,
            ProductMasterMapper products, AccountMapper accounts, UserService users) {
        this.orders = orders;
        this.settlements = settlements;
        this.funding = funding;
        this.txns = txns;
        this.postings = postings;
        this.products = products;
        this.accounts = accounts;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<SettlementAuditDTO> history() {
        Long userId = users.getCurrentUser().getId();
        List<SettlementAuditDTO> result = new ArrayList<>();
        for (Order order : orders.selectByUserId(userId)) {
            if (settlements.selectByOrderId(order.getOrderId()) != null) {
                result.add(build(order));
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public SettlementAuditDTO detail(String orderId) {
        Order order = orders.selectByOrderId(orderId);
        if (order == null || !Objects.equals(order.getUserId(), users.getCurrentUser().getId())) {
            return null;
        }
        return settlements.selectByOrderId(orderId) == null ? null : build(order);
    }

    private SettlementAuditDTO build(Order order) {
        SettlementConfirm settlement = settlements.selectByOrderId(order.getOrderId());
        SettlementAuditDTO audit = new SettlementAuditDTO();
        audit.setOrderId(order.getOrderId());
        audit.setOrderType(order.getOrderType());
        audit.setOrderStatus(order.getStatus());
        audit.setProductId(order.getProductId());
        ProductMaster product = products.selectById(order.getProductId());
        audit.setProductName(product == null ? null : product.getProductName());
        audit.setSettlement(settlement);
        audit.setFundingLines(funding.selectByOrderId(order.getOrderId()));
        List<LedgerTxn> related = txns.selectByOrderId(order.getOrderId());
        LedgerTxn settlementTxn = related == null ? null : related.stream()
                .filter(t -> settlement.getLedgerTxnId() != null
                        ? settlement.getLedgerTxnId().equals(t.getTxnId())
                        : t.getNote() != null && t.getNote().startsWith("订单结算:"))
                .findFirst().orElse(null);
        List<String> broken = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (!"CONFIRMED".equals(order.getStatus())) broken.add("结算存在但订单不是 CONFIRMED");
        if (settlement.getLedgerTxnId() == null) warnings.add("历史结算没有精确流水关联，按结算备注识别");
        if (settlement.getPreviewDigest() == null) warnings.add("历史结算没有预览摘要");
        if (settlementTxn == null) broken.add("结算流水缺失");
        audit.setLedgerTxnId(settlementTxn == null ? settlement.getLedgerTxnId() : settlementTxn.getTxnId());
        List<LedgerPosting> entries = settlementTxn == null ? List.of() : postings.selectByTxnId(settlementTxn.getTxnId());
        if (entries == null) entries = List.of();
        audit.setPostings(entries);
        if (entries.isEmpty()) broken.add("结算分录缺失");
        BigDecimal cash = signedTotal(entries, "CASH");
        BigDecimal shares = signedShares(entries, "POSITION");
        BigDecimal fee = total(entries, "FEE");
        audit.setCashDelta(cash);
        audit.setPositionSharesDelta(shares);
        audit.setFeeAmount(fee);
        boolean buy = "BUY".equals(order.getOrderType()) || "SUBSCRIPTION".equals(order.getOrderType());
        boolean sell = "SELL".equals(order.getOrderType()) || "REDEMPTION".equals(order.getOrderType());
        boolean linkedPosition = accounts.selectByLinkedProductId(order.getProductId()) != null;
        if (linkedPosition && settlement.getConfirmShares() != null) {
            // Linked products update initial_shares directly and have no POSITION posting.
            audit.setPositionSharesDelta(sell ? settlement.getConfirmShares().negate() : settlement.getConfirmShares());
        }
        if (!buy && !sell) broken.add("订单类型不支持结算核对");
        if (buy && !linkedPosition && (settlement.getConfirmShares() == null || shares.compareTo(settlement.getConfirmShares()) != 0))
            broken.add("持仓增加份额与结算确认份额不一致");
        if (sell && !linkedPosition && (settlement.getConfirmShares() == null || shares.compareTo(settlement.getConfirmShares().negate()) != 0))
            broken.add("持仓减少份额与结算确认份额不一致");
        if (sell && settlement.getConfirmAmount() != null && cash.compareTo(
                settlement.getConfirmAmount().subtract(orZero(settlement.getConfirmFee()))) != 0)
            broken.add("到账现金与结算金额扣费后不一致");
        if (fee.compareTo(orZero(settlement.getConfirmFee())) != 0) broken.add("手续费分录与结算确认金额不一致");
        if (buy && !linkedPosition && entries.stream().noneMatch(p -> "POSITION".equals(p.getAccountType())))
            broken.add("应有持仓分录缺失");
        if (linkedPosition) warnings.add("关联账户份额直接记录在账户余额，缺少不可变份额快照，需人工核对");
        if (buy && total(entries, "RECEIVABLE").compareTo(audit.getFundingLines().stream()
                .map(OrderFundingLine::getAmount).map(SettlementAuditService::orZero)
                .reduce(BigDecimal.ZERO, BigDecimal::add)) != 0)
            broken.add("应收分录与订单出资金额不一致");
        if (buy && !linkedPosition && settlement.getConfirmNav() != null
                && settlement.getConfirmShares() != null && total(entries, "POSITION").compareTo(
                settlement.getConfirmShares().multiply(settlement.getConfirmNav())
                        .setScale(2, java.math.RoundingMode.HALF_UP)) != 0)
            broken.add("持仓金额与结算份额及净值不一致");
        audit.setReconciliationStatus(!broken.isEmpty() ? "BROKEN" : !warnings.isEmpty() ? "WARNING" : "OK");
        broken.addAll(warnings);
        audit.setReasons(broken);
        return audit;
    }

    private static BigDecimal orZero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static BigDecimal total(List<LedgerPosting> rows, String type) {
        return rows.stream().filter(p -> type.equals(p.getAccountType()))
                .map(p -> orZero(p.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private static BigDecimal signedTotal(List<LedgerPosting> rows, String type) {
        return rows.stream().filter(p -> type.equals(p.getAccountType()))
                .map(p -> "CREDIT".equals(p.getPostingType()) ? orZero(p.getAmount()).negate() : orZero(p.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private static BigDecimal signedShares(List<LedgerPosting> rows, String type) {
        return rows.stream().filter(p -> type.equals(p.getAccountType()))
                .map(p -> "CREDIT".equals(p.getPostingType()) ? orZero(p.getShares()).negate() : orZero(p.getShares()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
