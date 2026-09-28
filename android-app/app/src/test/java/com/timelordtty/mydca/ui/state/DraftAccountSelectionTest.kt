package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.data.dto.MobileAccountDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 校验前端账户选择提示与后端 MobileWealthService 的安全口径一致：
 * selectableForDraft = 叶子 + REAL + 启用；selectableForExpense = selectableForDraft + SPENDABLE。
 * 这里只验证前端提示，最终放行仍由后端 preview / confirm 重新校验。
 */
class DraftAccountSelectionTest {
    private val spendable = account(1, "日常账户", "SPENDABLE", selectableForExpense = true)
    private val reserved = account(2, "专款账户", "RESERVED")
    private val investable = account(3, "投资账户", "INVESTABLE")
    private val unallocated = account(4, "待分配账户", null)
    private val parent = account(5, "父账户", "SPENDABLE", leaf = false, selectableForDraft = false)
    private val all = listOf(spendable, reserved, investable, unallocated, parent)

    @Test
    fun expenseOnlyAllowsSpendableLeafAccounts() {
        val selectable = DraftAccountSelection.selectableFor("EXPENSE", all)

        assertEquals(listOf(1L), selectable.map { it.id })
        assertTrue(DraftAccountSelection.isSelectable("EXPENSE", spendable))
        assertFalse(DraftAccountSelection.isSelectable("EXPENSE", reserved))
        assertFalse(DraftAccountSelection.isSelectable("EXPENSE", investable))
        assertFalse(DraftAccountSelection.isSelectable("EXPENSE", unallocated))
        assertFalse(DraftAccountSelection.isSelectable("EXPENSE", parent))
    }

    @Test
    fun incomeStillBlocksParentAccounts() {
        val selectable = DraftAccountSelection.selectableFor("INCOME", all)

        assertEquals(listOf(1L, 2L, 3L, 4L), selectable.map { it.id })
        assertFalse(DraftAccountSelection.isSelectable("INCOME", parent))
    }

    @Test
    fun rejectionReasonsExplainTheProtectedAccount() {
        assertNull(DraftAccountSelection.rejectionReason("EXPENSE", spendable))
        assertTrue(DraftAccountSelection.rejectionReason("EXPENSE", reserved).orEmpty().contains("RESERVED"))
        assertTrue(DraftAccountSelection.rejectionReason("EXPENSE", investable).orEmpty().contains("INVESTABLE"))
        assertTrue(DraftAccountSelection.rejectionReason("EXPENSE", unallocated).orEmpty().contains("待分配"))
        assertTrue(DraftAccountSelection.rejectionReason("EXPENSE", parent).orEmpty().contains("父账户"))
    }

    @Test
    fun typeMatchingIsCaseInsensitiveAndBlankSafe() {
        assertTrue(DraftAccountSelection.isSelectable("expense", spendable))
        assertFalse(DraftAccountSelection.isSelectable(" expense ", reserved))
        assertTrue(DraftAccountSelection.isSelectable(null, reserved))
    }

    @Test
    fun transferAllowsAnySelectableLeafAccountRegardlessOfFundUsage() {
        val selectable = DraftAccountSelection.selectableFor("TRANSFER", all)

        assertEquals(listOf(1L, 2L, 3L, 4L), selectable.map { it.id })
        assertTrue(DraftAccountSelection.isSelectable("transfer", reserved))
        assertTrue(DraftAccountSelection.isSelectable("TRANSFER", investable))
        assertFalse(DraftAccountSelection.isSelectable("TRANSFER", parent))
        assertNull(DraftAccountSelection.rejectionReason("TRANSFER", reserved))
        assertTrue(DraftAccountSelection.rejectionReason("TRANSFER", parent).orEmpty().contains("父账户"))
    }

    @Test
    fun transferValidationFlagsSameAccountAndCurrencyMismatch() {
        assertEquals(
            "转出账户与转入账户不能相同，请重新选择转入账户。",
            DraftAccountSelection.transferValidationMessage(spendable, spendable),
        )

        val cny = spendable.copy(currency = "CNY")
        val usd = account(6, "美元账户", "SPENDABLE").copy(currency = "USD")
        assertTrue(DraftAccountSelection.transferValidationMessage(cny, usd).orEmpty().contains("币种"))

        val anotherCny = account(7, "另一个日常账户", "SPENDABLE")
        assertNull(DraftAccountSelection.transferValidationMessage(cny, anotherCny))
        assertNull(DraftAccountSelection.transferValidationMessage(null, anotherCny))
    }
    @Test
    fun investmentOnlyAllowsInvestableLeafAccounts() {
        val selectable = DraftAccountSelection.selectableFor("BUY", all)

        assertEquals(listOf(3L), selectable.map { it.id })
        assertTrue(DraftAccountSelection.isSelectable("BUY", investable))
        assertTrue(DraftAccountSelection.isSelectable("subscription", investable))
        assertFalse(DraftAccountSelection.isSelectable("BUY", spendable))
        assertFalse(DraftAccountSelection.isSelectable("BUY", reserved))
        assertFalse(DraftAccountSelection.isSelectable("BUY", unallocated))
        assertFalse(DraftAccountSelection.isSelectable("BUY", parent))
    }

    @Test
    fun bondRepoMayUseReservedAccountOnly() {
        assertTrue(DraftAccountSelection.isSelectable("BUY", reserved, "BOND_REPO"))
        assertTrue(DraftAccountSelection.isSelectable("SUBSCRIPTION", reserved, "bond_repo"))
        assertTrue(DraftAccountSelection.isEligibleInvestmentAccount(reserved, "BOND_REPO"))
        assertNull(DraftAccountSelection.rejectionReason("BUY", reserved, "BOND_REPO"))

        assertFalse(DraftAccountSelection.isSelectable("BUY", reserved, "FUND"))
        assertFalse(DraftAccountSelection.isSelectable("BUY", spendable, "BOND_REPO"))
        assertFalse(DraftAccountSelection.isSelectable("BUY", parent, "BOND_REPO"))
    }

    @Test
    fun investmentRejectionReasonsExplainTheProtectedAccount() {
        assertNull(DraftAccountSelection.rejectionReason("BUY", investable))
        assertTrue(
            DraftAccountSelection.rejectionReason("BUY", spendable).orEmpty().contains("SPENDABLE"),
        )
        assertTrue(
            DraftAccountSelection.rejectionReason("SUBSCRIPTION", reserved).orEmpty().contains("RESERVED"),
        )
        assertTrue(
            DraftAccountSelection.rejectionReason("BUY", unallocated).orEmpty().contains("待分配"),
        )
        assertTrue(
            DraftAccountSelection.rejectionReason("BUY", parent).orEmpty().contains("父账户"),
        )
    }

    @Test
    fun sellRedeemTargetOnlyAllowsRealLeafNonPositionMatchingCurrency() {
        val selectable = DraftAccountSelection.selectableFor("SELL", all)

        assertEquals(listOf(1L, 2L, 3L, 4L), selectable.map { it.id })
        assertTrue(DraftAccountSelection.isSelectable("SELL", investable))
        assertTrue(DraftAccountSelection.isSelectable("redemption", reserved))
        assertFalse(DraftAccountSelection.isSelectable("SELL", parent))
        assertNull(DraftAccountSelection.rejectionReason("REDEMPTION", spendable))

        val position = account(8, "证券持仓账户", "INVESTABLE").copy(accountType = "POSITION")
        assertFalse(DraftAccountSelection.isSelectable("SELL", position))
        assertTrue(DraftAccountSelection.rejectionReason("SELL", position).orEmpty().contains("POSITION"))

        val cny = spendable.copy(currency = "CNY")
        val usd = account(9, "美元账户", "SPENDABLE").copy(currency = "USD")
        assertTrue(DraftAccountSelection.isSelectable("SELL", cny, productCurrency = "CNY"))
        assertFalse(DraftAccountSelection.isSelectable("SELL", usd, productCurrency = "CNY"))
        assertTrue(
            DraftAccountSelection.rejectionReason("SELL", usd, productCurrency = "CNY").orEmpty().contains("币种"),
        )
    }

    private fun account(
        id: Long,
        name: String,
        fundUsage: String?,
        leaf: Boolean = true,
        selectableForDraft: Boolean = leaf,
        selectableForExpense: Boolean = selectableForDraft && fundUsage == "SPENDABLE",
    ) = MobileAccountDto(
        id = id,
        accountName = name,
        fundUsage = fundUsage,
        leaf = leaf,
        selectableForDraft = selectableForDraft,
        selectableForExpense = selectableForExpense,
    )
}
