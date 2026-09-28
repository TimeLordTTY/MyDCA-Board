package com.timelordtty.dca.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.timelordtty.dca.mapper.AccountMapper;
import com.timelordtty.dca.model.Account;
import com.timelordtty.dca.model.LedgerPosting;
import com.timelordtty.dca.model.LedgerTxn;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证 quickTransfer 的权限边界和记账形态：只接受可见的真实叶子账户，创建一笔平衡转账，且拒绝非法输入。
 */
class QuickEntryServiceTransferTest {

    private final LedgerService ledgerService = mock(LedgerService.class);
    private final AccountMapper accountMapper = mock(AccountMapper.class);
    private final AccountService accountService = mock(AccountService.class);
    private final QuickEntryService service = new QuickEntryService(ledgerService, accountMapper, accountService);

    @Test
    void quickTransferCreatesSingleBalancedTransferTransaction() {
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(account(7L, "余额宝", "CNY"));
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(account(8L, "工资卡", "CNY"));
        when(accountService.isLeafAccount(7L)).thenReturn(true);
        when(accountService.isLeafAccount(8L)).thenReturn(true);
        LedgerTxn txn = new LedgerTxn();
        txn.setTxnId("TXN-TRANSFER");
        List<List<LedgerPosting>> capturedPostings = new ArrayList<>();
        when(ledgerService.createTransaction(eq(10L), eq(20L), eq("TRANSFER_OUT"), any(), any(), any()))
                .thenAnswer(invocation -> {
                    capturedPostings.add(invocation.getArgument(4));
                    return txn;
                });

        LedgerTxn created = service.quickTransfer(10L, 20L, 7L, 8L, new BigDecimal("100"), "资金分区");

        assertEquals("TXN-TRANSFER", created.getTxnId());
        assertEquals(1, capturedPostings.size());
        List<LedgerPosting> postings = capturedPostings.get(0);
        assertEquals(2, postings.size());
        BigDecimal debit = postings.stream()
                .filter(posting -> "DEBIT".equals(posting.getPostingType()))
                .map(LedgerPosting::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = postings.stream()
                .filter(posting -> "CREDIT".equals(posting.getPostingType()))
                .map(LedgerPosting::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("100"), credit);
        assertEquals(credit, debit);
        assertEquals(Long.valueOf(7L), postings.stream()
                .filter(posting -> "CREDIT".equals(posting.getPostingType())).findFirst().orElseThrow().getAccountId());
        assertEquals(Long.valueOf(8L), postings.stream()
                .filter(posting -> "DEBIT".equals(posting.getPostingType())).findFirst().orElseThrow().getAccountId());
    }

    @Test
    void quickTransferRejectsSameAccountWithoutPosting() {
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.quickTransfer(10L, 20L, 7L, 7L, new BigDecimal("100"), null));

        assertTrue(error.getMessage().contains("不能相同"));
        verify(ledgerService, never()).createTransaction(any(), any(), any(), any(), any(), any());
    }

    @Test
    void quickTransferRejectsNonPositiveAmountWithoutPosting() {
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.quickTransfer(10L, 20L, 7L, 8L, BigDecimal.ZERO, null));

        assertTrue(error.getMessage().contains("大于 0"));
        verify(ledgerService, never()).createTransaction(any(), any(), any(), any(), any(), any());
    }

    @Test
    void quickTransferRejectsInvisibleTargetAccount() {
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(account(7L, "余额宝", "CNY"));
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(null);

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.quickTransfer(10L, 20L, 7L, 8L, new BigDecimal("100"), null));

        assertTrue(error.getMessage().contains("转入账户"));
        verify(ledgerService, never()).createTransaction(any(), any(), any(), any(), any(), any());
    }

    @Test
    void quickTransferRejectsParentAccounts() {
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(account(7L, "父账户", "CNY"));
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(account(8L, "工资卡", "CNY"));
        when(accountService.isLeafAccount(7L)).thenReturn(false);

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.quickTransfer(10L, 20L, 7L, 8L, new BigDecimal("100"), null));

        assertTrue(error.getMessage().contains("叶子账户"));
        verify(ledgerService, never()).createTransaction(any(), any(), any(), any(), any(), any());
    }

    @Test
    void quickTransferRejectsMismatchedCurrency() {
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(account(7L, "余额宝", "CNY"));
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(account(8L, "美元账户", "USD"));
        when(accountService.isLeafAccount(7L)).thenReturn(true);
        when(accountService.isLeafAccount(8L)).thenReturn(true);

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.quickTransfer(10L, 20L, 7L, 8L, new BigDecimal("100"), null));

        assertTrue(error.getMessage().contains("币种"));
        verify(ledgerService, never()).createTransaction(any(), any(), any(), any(), any(), any());
    }

    @Test
    void quickTransferUsesNullFamilyScopeWhenNotProvided() {
        when(accountMapper.selectVisibleRealById(7L, 10L, null)).thenReturn(account(7L, "余额宝", "CNY"));
        when(accountMapper.selectVisibleRealById(8L, 10L, null)).thenReturn(account(8L, "工资卡", "CNY"));
        when(accountService.isLeafAccount(7L)).thenReturn(true);
        when(accountService.isLeafAccount(8L)).thenReturn(true);
        when(ledgerService.createTransaction(eq(10L), isNull(), eq("TRANSFER_OUT"), any(), any(), any()))
                .thenReturn(new LedgerTxn());

        service.quickTransfer(10L, null, 7L, 8L, new BigDecimal("66.66"), "个人转账");

        verify(ledgerService).createTransaction(eq(10L), isNull(), eq("TRANSFER_OUT"), any(), any(), eq("个人转账"));
    }

    private Account account(Long id, String name, String currency) {
        Account account = new Account();
        account.setId(id);
        account.setAccountName(name);
        account.setAccountKind("REAL");
        account.setAccountType("BANK");
        account.setCurrency(currency);
        account.setIsActive(true);
        return account;
    }
}
