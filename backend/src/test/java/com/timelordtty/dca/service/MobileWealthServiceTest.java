package com.timelordtty.dca.service;

import com.timelordtty.dca.model.Account;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MobileWealthServiceTest {
    @Test
    void fundUsageSummaryCountsRealCashLeavesOnly() {
        Account spendable = account("REAL", "CASH", "SPENDABLE", "120.50");
        Account reserved = account("REAL", "CASH", "RESERVED", "80.00");
        Account investable = account("REAL", "CASH", "INVESTABLE", "300.00");
        Account unallocated = account("REAL", "CASH", null, "20.00");
        Account virtual = account("VIRTUAL", "CASH", "SPENDABLE", "999.00");
        Account nonCash = account("REAL", "BANK", "SPENDABLE", "888.00");
        Account parent = account("REAL", "CASH", "SPENDABLE", "777.00");
        parent.setChildren(List.of(spendable));

        List<Account> accounts = List.of(spendable, reserved, investable, unallocated, virtual, nonCash, parent);

        assertEquals(new BigDecimal("120.50"), MobileWealthService.sumFundUsage(accounts, "SPENDABLE"));
        assertEquals(new BigDecimal("80.00"), MobileWealthService.sumFundUsage(accounts, "RESERVED"));
        assertEquals(new BigDecimal("300.00"), MobileWealthService.sumFundUsage(accounts, "INVESTABLE"));
        assertEquals(new BigDecimal("20.00"), MobileWealthService.sumUnallocated(accounts));
    }

    private Account account(String kind, String type, String usage, String balance) {
        Account account = new Account();
        account.setAccountKind(kind);
        account.setAccountType(type);
        account.setFundUsage(usage);
        account.setBalance(new BigDecimal(balance));
        return account;
    }
}
