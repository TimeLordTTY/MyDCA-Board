package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.data.dto.MobileAccountDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountFundUsageFilterTest {
    private val accounts = listOf(
        account(1, "日常账户", "SPENDABLE"),
        account(2, "专款账户", "RESERVED"),
        account(3, "投资账户", "INVESTABLE"),
        account(4, "新账户", null),
    )

    @Test
    fun everyFundUsageFilterShowsOnlyItsOwnAccounts() {
        assertEquals(accounts, accounts.filter(AccountFundUsageFilter.ALL::matches))
        assertEquals(listOf(1L), idsFor(AccountFundUsageFilter.SPENDABLE))
        assertEquals(listOf(2L), idsFor(AccountFundUsageFilter.RESERVED))
        assertEquals(listOf(3L), idsFor(AccountFundUsageFilter.INVESTABLE))
        assertEquals(listOf(4L), idsFor(AccountFundUsageFilter.UNALLOCATED))
    }

    @Test
    fun savedFilterRestoresAndUnknownValueFallsBackToAll() {
        assertEquals(
            AccountFundUsageFilter.RESERVED,
            AccountFundUsageFilter.fromSavedValue(AccountFundUsageFilter.RESERVED.name),
        )
        assertEquals(AccountFundUsageFilter.ALL, AccountFundUsageFilter.fromSavedValue("UNKNOWN"))
    }

    @Test
    fun refreshDoesNotNeedToResetTheSavedFilter() {
        val savedValue = AccountFundUsageFilter.INVESTABLE.name
        val refreshedAccounts = accounts + account(5, "第二投资账户", "INVESTABLE")

        val restoredFilter = AccountFundUsageFilter.fromSavedValue(savedValue)

        assertEquals(listOf(3L, 5L), refreshedAccounts.filter(restoredFilter::matches).map { it.id })
    }

    @Test
    fun messagesIdentifyTheCurrentFilter() {
        assertEquals("暂无可支出账户", AccountFundUsageFilter.SPENDABLE.emptyMessage)
        assertEquals("专款账户加载失败", AccountFundUsageFilter.RESERVED.errorTitle())
    }

    @Test
    fun filteringDoesNotChangeAccountSafetyFlags() {
        val parent = account(6, "父账户", "SPENDABLE", leaf = false, selectableForExpense = false)
        val reserved = account(7, "专款", "RESERVED", selectableForExpense = false)
        val investable = account(8, "投资", "INVESTABLE", selectableForExpense = false)

        assertTrue(AccountFundUsageFilter.SPENDABLE.matches(parent))
        assertFalse(parent.leaf)
        assertFalse(parent.selectableForExpense)
        assertFalse(reserved.selectableForExpense)
        assertFalse(investable.selectableForExpense)
    }

    private fun idsFor(filter: AccountFundUsageFilter): List<Long> =
        accounts.filter(filter::matches).map { it.id }

    private fun account(
        id: Long,
        name: String,
        fundUsage: String?,
        leaf: Boolean = true,
        selectableForExpense: Boolean = fundUsage == "SPENDABLE",
    ) = MobileAccountDto(
        id = id,
        accountName = name,
        fundUsage = fundUsage,
        leaf = leaf,
        selectableForExpense = selectableForExpense,
    )
}
