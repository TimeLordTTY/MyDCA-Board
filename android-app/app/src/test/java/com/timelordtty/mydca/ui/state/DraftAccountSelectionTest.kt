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
