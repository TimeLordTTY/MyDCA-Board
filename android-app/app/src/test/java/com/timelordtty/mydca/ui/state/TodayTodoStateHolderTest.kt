package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.data.api.WealthHubApi
import com.timelordtty.mydca.data.dto.AccountingIntentDto
import com.timelordtty.mydca.data.dto.DraftFromIntentRequestDto
import com.timelordtty.mydca.data.dto.DraftFromIntentResponseDto
import com.timelordtty.mydca.data.dto.DraftLedgerEntryDto
import com.timelordtty.mydca.data.dto.DraftPreviewDto
import com.timelordtty.mydca.data.dto.IgnoreDraftRequestDto
import com.timelordtty.mydca.data.dto.MobileAccountDto
import com.timelordtty.mydca.data.dto.MobileCashFlowDto
import com.timelordtty.mydca.data.dto.MobileHoldingByAccountDto
import com.timelordtty.mydca.data.dto.MobileHoldingDto
import com.timelordtty.mydca.data.dto.MobileOverviewDto
import com.timelordtty.mydca.data.dto.MobilePageDto
import com.timelordtty.mydca.data.dto.MobileTransactionDto
import com.timelordtty.mydca.data.dto.PendingSettlementOrderDto
import com.timelordtty.mydca.data.dto.ParseTextRequestDto
import com.timelordtty.mydca.data.dto.ProductDto
import com.timelordtty.mydca.data.dto.SettlementConfirmDto
import com.timelordtty.mydca.data.dto.SettlementPreviewDto
import com.timelordtty.mydca.data.dto.SettlementPreviewRequestDto
import com.timelordtty.mydca.data.dto.TodayTodoDto
import com.timelordtty.mydca.data.dto.UpdateDraftRequestDto
import com.timelordtty.mydca.data.repository.TodoRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 今日待办只读装载：刷新失败时保留上一次成功结果，仅提示错误。
 */
class TodayTodoStateHolderTest {
    @Test
    fun successStoresTodosAndTimestamp() = runTest {
        val holder = TodayTodoStateHolder(
            repository = TodoRepository(TodoApiFixture(TodayTodoDto(totalCount = 2, draftCount = 1))),
            clock = { 1_700_000_000_000L },
        )

        val state = holder.load()

        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
        assertEquals(2, state.todos?.totalCount)
        assertEquals(1, state.todos?.draftCount)
        assertEquals(1_700_000_000_000L, state.lastUpdatedAt)
        assertNull(state.errorMessage)
    }

    @Test
    fun failureKeepsPreviousTodosAndOnlyReportsError() = runTest {
        val holder = TodayTodoStateHolder(
            repository = TodoRepository(TodoApiFixture(fail = true)),
            clock = { 2L },
        )
        val previous = TodayTodoUiState(
            isLoading = false,
            todos = TodayTodoDto(totalCount = 3),
            lastUpdatedAt = 99L,
        )

        val state = holder.load(previous)

        assertEquals(3, state.todos?.totalCount)
        assertEquals(99L, state.lastUpdatedAt)
        assertNotNull(state.errorMessage)
        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
    }
}

private class TodoApiFixture(
    private val todo: TodayTodoDto? = null,
    private val fail: Boolean = false,
) : WealthHubApi {
    override suspend fun getFinanceRadar(): com.timelordtty.mydca.data.dto.FinanceRadarDto = throw UnsupportedOperationException()
    override suspend fun goals(page: Int, size: Int): List<com.timelordtty.mydca.data.dto.GoalDto> = error("Not used in this fixture")
    override suspend fun goalProgress(id: String): com.timelordtty.mydca.data.dto.GoalProgressDto = error("Not used in this fixture")
    override suspend fun monthlyBudgets(page: Int, size: Int): List<com.timelordtty.mydca.data.dto.BudgetDto> = error("Not used in this fixture")
    override suspend fun budgetComparison(id: String): com.timelordtty.mydca.data.dto.BudgetComparisonDto = error("Not used in this fixture")
    override suspend fun allocationPolicies(page: Int, size: Int): List<com.timelordtty.mydca.data.dto.AllocationPolicyDto> = error("Not used in this fixture")
    override suspend fun allocationPolicy(id: String): com.timelordtty.mydca.data.dto.AllocationPolicyDto = error("Not used in this fixture")
    override suspend fun allocationObservation(id: String): com.timelordtty.mydca.data.dto.AllocationEvaluationDto = error("Not used in this fixture")
    override suspend fun allocationPreview(id: String): com.timelordtty.mydca.data.dto.AllocationPreviewDto = error("Not used in this fixture")
    override suspend fun riskRules(page: Int, size: Int): List<com.timelordtty.mydca.data.dto.RiskRuleDto> = error("Not used in this fixture")
    override suspend fun riskSnapshots(id: String, page: Int, size: Int): List<com.timelordtty.mydca.data.dto.RiskSnapshotDto> = error("Not used in this fixture")
    override suspend fun riskEvents(id: String, page: Int, size: Int): List<com.timelordtty.mydca.data.dto.RiskEventDto> = error("Not used in this fixture")
    override suspend fun researchPlans(page: Int, size: Int): List<com.timelordtty.mydca.data.dto.ResearchPlanDto> = error("Not used in this fixture")
    override suspend fun researchPlan(id: String): com.timelordtty.mydca.data.dto.ResearchPlanDto = error("Not used in this fixture")
    override suspend fun researchRuns(page: Int, size: Int): List<com.timelordtty.mydca.data.dto.ResearchRunDto> = error("Not used in this fixture")
    override suspend fun recentBacktests(): List<com.timelordtty.mydca.data.dto.BacktestResultDto> = emptyList()
    override suspend fun draftHistory(draftId: Long): List<com.timelordtty.mydca.data.dto.DraftLifecycleEventDto> = throw UnsupportedOperationException()
    override suspend fun reopenDraft(draftId: Long): DraftLedgerEntryDto = throw UnsupportedOperationException()
    override suspend fun copyConfirmedDraft(draftId: Long): DraftLedgerEntryDto = throw UnsupportedOperationException()
    override suspend fun getTodayTodos(): TodayTodoDto {
        if (fail) throw IllegalStateException("待办加载失败")
        return todo ?: TodayTodoDto()
    }

    override suspend fun listDrafts(status: String?, page: Int, pageSize: Int): List<DraftLedgerEntryDto> =
        throw UnsupportedOperationException()

    override suspend fun getDraft(draftId: Long): DraftLedgerEntryDto = throw UnsupportedOperationException()

    override suspend fun updateDraft(draftId: Long, request: UpdateDraftRequestDto): DraftLedgerEntryDto =
        throw UnsupportedOperationException()

    override suspend fun previewDraft(draftId: Long): DraftPreviewDto = throw UnsupportedOperationException()

    override suspend fun confirmDraft(draftId: Long): DraftLedgerEntryDto = throw UnsupportedOperationException()

    override suspend fun ignoreDraft(draftId: Long, request: IgnoreDraftRequestDto): DraftLedgerEntryDto =
        throw UnsupportedOperationException()

    override suspend fun parseAccountingText(request: ParseTextRequestDto): AccountingIntentDto =
        throw UnsupportedOperationException()

    override suspend fun draftFromIntent(request: DraftFromIntentRequestDto): DraftFromIntentResponseDto =
        throw UnsupportedOperationException()

    override suspend fun getMobileOverview(): MobileOverviewDto = throw UnsupportedOperationException()

    override suspend fun getMobileAccounts(page: Int, pageSize: Int): MobilePageDto<MobileAccountDto> =
        throw UnsupportedOperationException()

    override suspend fun getMobileAccountDetail(accountId: Long): MobileAccountDto =
        throw UnsupportedOperationException()

    override suspend fun getMobileCashFlow(): MobileCashFlowDto = throw UnsupportedOperationException()

    override suspend fun getMobileTransactions(page: Int, pageSize: Int): MobilePageDto<MobileTransactionDto> =
        throw UnsupportedOperationException()

    override suspend fun getMobileHoldings(page: Int, pageSize: Int): MobilePageDto<MobileHoldingDto> =
        throw UnsupportedOperationException()

    override suspend fun getProducts(keyword: String?, assetType: String?, channel: String?): List<ProductDto> =
        throw UnsupportedOperationException()

    override suspend fun getProductHoldingsByAccount(productId: Long): List<MobileHoldingByAccountDto> =
        throw UnsupportedOperationException()

    override suspend fun getPendingSettlements(): List<PendingSettlementOrderDto> =
        throw UnsupportedOperationException()
    override suspend fun getSettlementHistory(): List<com.timelordtty.mydca.data.dto.SettlementAuditDto> = throw UnsupportedOperationException()
    override suspend fun getSettlementAudit(orderId: String): com.timelordtty.mydca.data.dto.SettlementAuditDto = throw UnsupportedOperationException()

    override suspend fun previewSettlement(request: SettlementPreviewRequestDto): SettlementPreviewDto =
        throw UnsupportedOperationException()

    override suspend fun confirmSettlement(request: SettlementPreviewRequestDto): SettlementConfirmDto =
        throw UnsupportedOperationException()
}
