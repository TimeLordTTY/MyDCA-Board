package com.timelordtty.mydca.data.repository

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
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class WealthRepositoryTest {
    @Test
    fun getOverviewReturnsSuccess() = runTest {
        val repository = WealthRepository(
            api = object : FakeWealthHubApi() {
                override suspend fun getMobileOverview(): MobileOverviewDto {
                    return MobileOverviewDto(totalAssets = "123.45", accountCount = 2)
                }
            }
        )

        val result = repository.getOverview()

        assertTrue(result is NetworkResult.Success)
        assertEquals("123.45", (result as NetworkResult.Success).data.totalAssets)
    }

    @Test
    fun getOverviewMaps401() = runTest {
        val repository = WealthRepository(
            api = object : FakeWealthHubApi() {
                override suspend fun getMobileOverview(): MobileOverviewDto {
                    throw HttpException(Response.error<MobileOverviewDto>(401, "".toResponseBody("text/plain".toMediaType())))
                }
            }
        )

        val result = repository.getOverview()

        assertTrue(result is NetworkResult.Failure)
        assertEquals("登录已失效，请重新登录", (result as NetworkResult.Failure).message)
    }

    @Test
    fun throwableMapperHandlesTimeoutAndServerError() {
        assertEquals("请求超时，请稍后重试", java.net.SocketTimeoutException().toUserMessage())
        val serverError = HttpException(Response.error<Any>(500, "".toResponseBody("text/plain".toMediaType())))
        assertEquals("服务暂时不可用，请稍后重试", serverError.toUserMessage())
    }
}

private open class FakeWealthHubApi : WealthHubApi {
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
