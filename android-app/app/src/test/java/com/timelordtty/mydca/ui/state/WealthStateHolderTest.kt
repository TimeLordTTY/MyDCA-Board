package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.api.WealthHubApi
import com.timelordtty.mydca.data.dto.DraftFromIntentRequestDto
import com.timelordtty.mydca.data.dto.DraftLedgerEntryDto
import com.timelordtty.mydca.data.dto.DraftPreviewDto
import com.timelordtty.mydca.data.dto.IgnoreDraftRequestDto
import com.timelordtty.mydca.data.dto.MobileAccountDto
import com.timelordtty.mydca.data.dto.MobileHoldingDto
import com.timelordtty.mydca.data.dto.MobileOverviewDto
import com.timelordtty.mydca.data.dto.MobilePageDto
import com.timelordtty.mydca.data.dto.MobileTransactionDto
import com.timelordtty.mydca.data.dto.ParseTextRequestDto
import com.timelordtty.mydca.data.dto.TodayTodoDto
import com.timelordtty.mydca.data.dto.UpdateDraftRequestDto
import com.timelordtty.mydca.data.repository.WealthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WealthStateHolderTest {
    @Test
    fun loadOverviewReturnsSuccessState() = runTest {
        val holder = WealthStateHolder(object : WealthRepository(FailingWealthApi()) {
            override suspend fun getOverview(): NetworkResult<MobileOverviewDto> {
                return NetworkResult.Success(MobileOverviewDto(totalAssets = "200.00", accountCount = 1))
            }
        })

        val state = holder.loadOverview()

        assertFalse(state.isLoading)
        assertEquals("200.00", state.overview?.totalAssets)
        assertNull(state.errorMessage)
    }

    @Test
    fun loadOverviewReturnsEmptyStateForTrulyEmptyPayload() = runTest {
        val holder = WealthStateHolder(object : WealthRepository(FailingWealthApi()) {
            override suspend fun getOverview(): NetworkResult<MobileOverviewDto> {
                return NetworkResult.Success(MobileOverviewDto())
            }
        })

        val state = holder.loadOverview()

        assertNull(state.overview)
    }

    @Test
    fun loadAssetsPropagatesFirstErrorAndKeepsSuccessfulPages() = runTest {
        val holder = WealthStateHolder(object : WealthRepository(FailingWealthApi()) {
            override suspend fun getAccounts(page: Int, pageSize: Int): NetworkResult<MobilePageDto<MobileAccountDto>> {
                return NetworkResult.Failure("账户超时")
            }

            override suspend fun getTransactions(page: Int, pageSize: Int): NetworkResult<MobilePageDto<MobileTransactionDto>> {
                return NetworkResult.Success(MobilePageDto(total = 1))
            }

            override suspend fun getHoldings(page: Int, pageSize: Int): NetworkResult<MobilePageDto<MobileHoldingDto>> {
                return NetworkResult.Success(MobilePageDto(total = 2))
            }
        })

        val state = holder.loadAssets()

        assertEquals("账户超时", state.errorMessage)
        assertEquals(1, state.transactions.total)
        assertEquals(2, state.holdings.total)
    }
}

private class FailingWealthApi : WealthHubApi {
    override suspend fun getTodayTodos(): TodayTodoDto = throw UnsupportedOperationException()
    override suspend fun listDrafts(status: String?, page: Int, pageSize: Int): List<DraftLedgerEntryDto> = throw UnsupportedOperationException()
    override suspend fun getDraft(draftId: Long): DraftLedgerEntryDto = throw UnsupportedOperationException()
    override suspend fun updateDraft(draftId: Long, request: UpdateDraftRequestDto): DraftLedgerEntryDto = throw UnsupportedOperationException()
    override suspend fun previewDraft(draftId: Long): DraftPreviewDto = throw UnsupportedOperationException()
    override suspend fun confirmDraft(draftId: Long): DraftLedgerEntryDto = throw UnsupportedOperationException()
    override suspend fun ignoreDraft(draftId: Long, request: IgnoreDraftRequestDto): DraftLedgerEntryDto = throw UnsupportedOperationException()
    override suspend fun parseAccountingText(request: ParseTextRequestDto) = throw UnsupportedOperationException()
    override suspend fun draftFromIntent(request: DraftFromIntentRequestDto) = throw UnsupportedOperationException()
    override suspend fun getMobileOverview(): MobileOverviewDto = throw UnsupportedOperationException()
    override suspend fun getMobileAccounts(page: Int, pageSize: Int): MobilePageDto<MobileAccountDto> = throw UnsupportedOperationException()
    override suspend fun getMobileAccountDetail(accountId: Long): MobileAccountDto = throw UnsupportedOperationException()
    override suspend fun getMobileTransactions(page: Int, pageSize: Int): MobilePageDto<MobileTransactionDto> = throw UnsupportedOperationException()
    override suspend fun getMobileHoldings(page: Int, pageSize: Int): MobilePageDto<MobileHoldingDto> = throw UnsupportedOperationException()
}
