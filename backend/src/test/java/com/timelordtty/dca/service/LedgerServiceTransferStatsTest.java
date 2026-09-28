package com.timelordtty.dca.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.dto.LedgerStatsPostingDTO;
import com.timelordtty.dca.dto.LedgerStatsQueryDTO;
import com.timelordtty.dca.dto.LedgerStatsSummaryDTO;
import com.timelordtty.dca.mapper.AccountMapper;
import com.timelordtty.dca.mapper.LedgerPostingMapper;
import com.timelordtty.dca.mapper.LedgerTxnMapper;
import com.timelordtty.dca.mapper.ProductMasterMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证 TRANSFER_OUT / TRANSFER_IN 只进入转账口径，不计入收入或支出统计，也不重复计数复式分录。
 */
class LedgerServiceTransferStatsTest {

    private final LedgerTxnMapper ledgerTxnMapper = mock(LedgerTxnMapper.class);
    private final LedgerPostingMapper ledgerPostingMapper = mock(LedgerPostingMapper.class);
    private final LedgerService service = new LedgerService(ledgerTxnMapper, ledgerPostingMapper,
            mock(AccountMapper.class), mock(AccountService.class), mock(ProductMasterMapper.class),
            mock(UserService.class));

    @Test
    void transferTransactionIsNotCountedAsIncomeOrExpense() {
        when(ledgerTxnMapper.selectStatsPostings(any())).thenReturn(List.of(
                posting("TXN-T1", "CREDIT", 7L, new BigDecimal("300.00")),
                posting("TXN-T1", "DEBIT", 8L, new BigDecimal("300.00"))));

        LedgerStatsSummaryDTO summary = service.getLedgerStatsSummary(user(), new LedgerStatsQueryDTO());

        assertEquals(new BigDecimal("0"), summary.getTotalIncome());
        assertEquals(new BigDecimal("0"), summary.getTotalExpense());
        assertEquals(0, summary.getIncomeTxnCount());
        assertEquals(0, summary.getExpenseTxnCount());
        assertEquals(new BigDecimal("0"), summary.getNetCashflow());
        assertEquals(new BigDecimal("300.00"), summary.getTransferAmount());
        assertEquals(1, summary.getTxnCount());
    }

    private AuthResponse.UserInfo user() {
        AuthResponse.UserInfo user = new AuthResponse.UserInfo();
        user.setId(10L);
        user.setFamilyId(20L);
        return user;
    }

    private LedgerStatsPostingDTO posting(String txnId, String postingType, Long accountId, BigDecimal amount) {
        LedgerStatsPostingDTO row = new LedgerStatsPostingDTO();
        row.setTxnId(txnId);
        row.setTxnType("TRANSFER_OUT");
        row.setTradeDate(LocalDate.of(2026, 9, 28));
        row.setPostingType(postingType);
        row.setAccountId(accountId);
        row.setAccountKind("REAL");
        row.setAccountType("CASH");
        row.setAmount(amount);
        return row;
    }
}
