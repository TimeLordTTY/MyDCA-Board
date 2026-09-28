package com.timelordtty.dca.service;

import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.dto.SettlementAuditDTO;
import com.timelordtty.dca.mapper.*;
import com.timelordtty.dca.model.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SettlementAuditServiceTest {
    private final OrderMapper orders = mock(OrderMapper.class);
    private final SettlementConfirmMapper settlements = mock(SettlementConfirmMapper.class);
    private final OrderFundingLineMapper funding = mock(OrderFundingLineMapper.class);
    private final LedgerTxnMapper txns = mock(LedgerTxnMapper.class);
    private final LedgerPostingMapper postings = mock(LedgerPostingMapper.class);
    private final ProductMasterMapper products = mock(ProductMasterMapper.class);
    private final AccountMapper accounts = mock(AccountMapper.class);
    private final UserService users = mock(UserService.class);
    private final SettlementAuditService service = new SettlementAuditService(
            orders, settlements, funding, txns, postings, products, accounts, users);

    private void fixture(String type) {
        AuthResponse.UserInfo user = new AuthResponse.UserInfo();
        user.setId(1L);
        when(users.getCurrentUser()).thenReturn(user);
        Order order = new Order();
        order.setOrderId("ORD-1"); order.setUserId(1L); order.setProductId(2L);
        order.setOrderType(type); order.setStatus("CONFIRMED");
        when(orders.selectByOrderId("ORD-1")).thenReturn(order);
        SettlementConfirm settlement = new SettlementConfirm();
        settlement.setOrderId("ORD-1"); settlement.setLedgerTxnId("TXN-1");
        settlement.setPreviewDigest("audit-digest");
        settlement.setConfirmShares(new BigDecimal("10"));
        settlement.setConfirmNav(new BigDecimal("9.8"));
        settlement.setConfirmAmount(new BigDecimal("100"));
        settlement.setConfirmFee(new BigDecimal("2"));
        when(settlements.selectByOrderId("ORD-1")).thenReturn(settlement);
        OrderFundingLine line = new OrderFundingLine();
        line.setAccountId(3L); line.setAmount(new BigDecimal("100")); line.setShares(new BigDecimal("10"));
        when(funding.selectByOrderId("ORD-1")).thenReturn(List.of(line));
        LedgerTxn txn = new LedgerTxn(); txn.setTxnId("TXN-1"); txn.setOrderId("ORD-1");
        when(txns.selectByOrderId("ORD-1")).thenReturn(List.of(txn));
        boolean buy = "BUY".equals(type) || "SUBSCRIPTION".equals(type);
        when(postings.selectByTxnId("TXN-1")).thenReturn(buy
                ? List.of(posting("RECEIVABLE", "CREDIT", "100", null),
                          posting("POSITION", "DEBIT", "98", "10"), posting("FEE", "DEBIT", "2", null))
                : List.of(posting("CASH", "DEBIT", "98", null),
                          posting("POSITION", "CREDIT", "100", "10"), posting("FEE", "DEBIT", "2", null)));
    }

    private static LedgerPosting posting(String type, String direction, String amount, String shares) {
        LedgerPosting row = new LedgerPosting();
        row.setAccountType(type); row.setPostingType(direction); row.setAmount(new BigDecimal(amount));
        row.setShares(shares == null ? null : new BigDecimal(shares));
        return row;
    }

    @Test void allFourOrderTypesReconcileWithoutWrites() {
        for (String type : List.of("BUY", "SUBSCRIPTION", "SELL", "REDEMPTION")) {
            reset(orders, settlements, funding, txns, postings, products, accounts, users);
            fixture(type);
            SettlementAuditDTO audit = service.detail("ORD-1");
            assertEquals("OK", audit.getReconciliationStatus(), type + audit.getReasons());
            assertEquals("TXN-1", audit.getLedgerTxnId());
            verify(orders, never()).update(any());
            verify(settlements, never()).insert(any());
            verify(postings, never()).insert(any());
        }
    }

    @Test void missingPostingAndWrongSharesAreBroken() {
        fixture("SELL");
        when(postings.selectByTxnId("TXN-1")).thenReturn(List.of());
        assertEquals("BROKEN", service.detail("ORD-1").getReconciliationStatus());
        when(postings.selectByTxnId("TXN-1")).thenReturn(List.of(
                posting("CASH", "DEBIT", "98", null), posting("POSITION", "CREDIT", "100", "9"),
                posting("FEE", "DEBIT", "2", null)));
        assertTrue(service.detail("ORD-1").getReasons().stream().anyMatch(r -> r.contains("份额")));
    }

    @Test void wrongCashAmountIsBroken() {
        fixture("REDEMPTION");
        when(postings.selectByTxnId("TXN-1")).thenReturn(List.of(
                posting("CASH", "DEBIT", "97", null), posting("POSITION", "CREDIT", "100", "10"),
                posting("FEE", "DEBIT", "2", null)));
        assertTrue(service.detail("ORD-1").getReasons().stream().anyMatch(r -> r.contains("到账现金")));
    }

    @Test void anotherOwnerCannotReadAudit() {
        fixture("BUY");
        orders.selectByOrderId("ORD-1").setUserId(99L);
        assertNull(service.detail("ORD-1"));
        verify(settlements, never()).selectByOrderId(anyString());
    }
}
