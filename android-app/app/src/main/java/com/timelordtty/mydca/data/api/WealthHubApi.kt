package com.timelordtty.mydca.data.api

import com.timelordtty.mydca.data.dto.DraftLedgerEntryDto
import com.timelordtty.mydca.data.dto.AccountingIntentDto
import com.timelordtty.mydca.data.dto.DraftFromIntentRequestDto
import com.timelordtty.mydca.data.dto.DraftFromIntentResponseDto
import com.timelordtty.mydca.data.dto.DraftPreviewDto
import com.timelordtty.mydca.data.dto.IgnoreDraftRequestDto
import com.timelordtty.mydca.data.dto.ParseTextRequestDto
import com.timelordtty.mydca.data.dto.TodayTodoDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Android 侧 WealthHub API 边界。
 * 只声明查看、预览和用户手动确认相关接口；移动端不直接写数据库。
 */
interface WealthHubApi {
    @GET("api/v2/todos/today")
    suspend fun getTodayTodos(): TodayTodoDto

    @GET("api/v2/drafts")
    suspend fun listDrafts(
        @Query("status") status: String? = "DRAFT",
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 50,
    ): List<DraftLedgerEntryDto>

    @GET("api/v2/drafts/{draftId}")
    suspend fun getDraft(@Path("draftId") draftId: Long): DraftLedgerEntryDto

    @POST("api/v2/drafts/{draftId}/preview")
    suspend fun previewDraft(@Path("draftId") draftId: Long): DraftPreviewDto

    @POST("api/v2/drafts/{draftId}/confirm")
    suspend fun confirmDraft(@Path("draftId") draftId: Long): DraftLedgerEntryDto

    @POST("api/v2/drafts/{draftId}/ignore")
    suspend fun ignoreDraft(
        @Path("draftId") draftId: Long,
        @Body request: IgnoreDraftRequestDto = IgnoreDraftRequestDto(),
    ): DraftLedgerEntryDto

    @POST("api/v2/ai/accounting/parse-text")
    suspend fun parseAccountingText(@Body request: ParseTextRequestDto): AccountingIntentDto

    @POST("api/v2/ai/accounting/draft-from-intent")
    suspend fun draftFromIntent(@Body request: DraftFromIntentRequestDto): DraftFromIntentResponseDto
}
