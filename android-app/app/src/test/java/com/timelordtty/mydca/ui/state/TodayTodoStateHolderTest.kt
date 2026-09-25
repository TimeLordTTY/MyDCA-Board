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
import com.timelordtty.mydca.data.dto.MobileHoldingDto
import com.timelordtty.mydca.data.dto.MobileOverviewDto
import com.timelordtty.mydca.data.dto.MobilePageDto
import com.timelordtty.mydca.data.dto.MobileTransactionDto
import com.timelordtty.mydca.data.dto.ParseTextRequestDto
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
}
