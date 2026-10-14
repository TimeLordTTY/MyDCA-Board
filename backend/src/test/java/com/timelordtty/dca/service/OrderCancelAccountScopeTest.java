package com.timelordtty.dca.service;

import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.mapper.*;
import com.timelordtty.dca.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 完全离线：验证本人订单也不能取消当前身份不可见账户的资金影响。 */
class OrderCancelAccountScopeTest {
    private final OrderMapper orders = mock(OrderMapper.class);
    private final AccountMapper accounts = mock(AccountMapper.class);
    private final OrderFundingLineMapper funding = mock(OrderFundingLineMapper.class);
    private final LedgerTxnMapper txns = mock(LedgerTxnMapper.class);
    private final LedgerPostingMapper postings = mock(LedgerPostingMapper.class);
    private final UserService users = mock(UserService.class);
    private final OrderService service = new OrderService(orders, accounts, mock(AccountService.class), funding,
            mock(SettlementConfirmMapper.class), txns, postings, mock(LedgerService.class), users, mock(ProductMasterMapper.class));
    @BeforeEach void setup() {
        AuthResponse.UserInfo user = new AuthResponse.UserInfo(); user.setId(1L); user.setFamilyId(10L);
        when(users.getCurrentUser()).thenReturn(user);
        Order order = new Order(); order.setOrderId("OWNED"); order.setUserId(1L); order.setStatus("PENDING");
        when(orders.selectByOrderId("OWNED")).thenReturn(order);
    }
    @Test void foreignFundingIsRejectedBeforeEarlierVisibleFundingIsWritten() {
        when(funding.selectByOrderId("OWNED")).thenReturn(List.of(line(100L), line(200L)));
        when(accounts.selectById(100L)).thenReturn(account(100L, 1L, null));
        when(accounts.selectById(200L)).thenReturn(account(200L, 2L, 20L));
        assertThrows(RuntimeException.class, () -> service.cancelOrder("OWNED"));
        verifyNoWrites();
    }
    @Test void foreignLedgerAccountIsRejectedBeforeAnyBalanceOrLedgerWrite() {
        LedgerTxn txn = new LedgerTxn(); txn.setTxnId("TXN");
        when(txns.selectByOrderId("OWNED")).thenReturn(List.of(txn));
        LedgerPosting first = posting(100L); LedgerPosting foreign = posting(200L);
        when(postings.selectByTxnId("TXN")).thenReturn(List.of(first, foreign));
        when(accounts.selectById(100L)).thenReturn(account(100L, 1L, null));
        when(accounts.selectById(200L)).thenReturn(account(200L, 2L, 20L));
        assertThrows(RuntimeException.class, () -> service.cancelOrder("OWNED"));
        verifyNoWrites();
    }
    @Test void visibleFamilyFundingRemainsCancellable() {
        when(funding.selectByOrderId("OWNED")).thenReturn(List.of(line(100L)));
        when(accounts.selectById(100L)).thenReturn(account(100L, 2L, 10L));
        assertDoesNotThrow(() -> service.cancelOrder("OWNED"));
        verify(accounts).updateReservedAmount(100L, BigDecimal.ZERO);
        verify(orders).update(argThat(o -> "CANCELLED".equals(o.getStatus())));
    }
    private void verifyNoWrites() {
        verify(accounts, never()).updateBalance(any(), any());
        verify(accounts, never()).updateReservedAmount(any(), any());
        verify(postings, never()).deleteByTxnId(any()); verify(txns, never()).deleteByTxnId(any());
        verify(funding, never()).deleteByOrderId(any()); verify(orders, never()).update(any());
    }
    private static OrderFundingLine line(Long id) {
        OrderFundingLine line = new OrderFundingLine(); line.setAccountId(id); line.setAmount(BigDecimal.TEN); return line;
    }
    private static LedgerPosting posting(Long id) {
        LedgerPosting posting = new LedgerPosting(); posting.setAccountId(id); posting.setPostingType("CREDIT");
        posting.setAmount(BigDecimal.TEN); return posting;
    }
    private static Account account(Long id, Long owner, Long family) {
        Account account = new Account(); account.setId(id); account.setOwnerUserId(owner); account.setOwnerFamilyId(family);
        account.setBalance(BigDecimal.TEN); account.setReservedAmount(BigDecimal.TEN); return account;
    }
}
