package com.timelordtty.mydca.data.api

import com.timelordtty.mydca.data.dto.DraftLedgerEntryDto
import com.timelordtty.mydca.data.dto.DraftLifecycleEventDto
import com.timelordtty.mydca.data.dto.AccountingIntentDto
import com.timelordtty.mydca.data.dto.DraftFromIntentRequestDto
import com.timelordtty.mydca.data.dto.DraftFromIntentResponseDto
import com.timelordtty.mydca.data.dto.DraftPreviewDto
import com.timelordtty.mydca.data.dto.IgnoreDraftRequestDto
import com.timelordtty.mydca.data.dto.MobileAccountDto
import com.timelordtty.mydca.data.dto.MobileCashFlowDto
import com.timelordtty.mydca.data.dto.MobileHoldingByAccountDto
import com.timelordtty.mydca.data.dto.MobileHoldingDto
import com.timelordtty.mydca.data.dto.MobileOverviewDto
import com.timelordtty.mydca.data.dto.MobilePageDto
import com.timelordtty.mydca.data.dto.MobileTransactionDto
import com.timelordtty.mydca.data.dto.ParseTextRequestDto
import com.timelordtty.mydca.data.dto.PendingSettlementOrderDto
import com.timelordtty.mydca.data.dto.ProductDto
import com.timelordtty.mydca.data.dto.SettlementConfirmDto
import com.timelordtty.mydca.data.dto.SettlementAuditDto
import com.timelordtty.mydca.data.dto.SettlementPreviewDto
import com.timelordtty.mydca.data.dto.SettlementPreviewRequestDto
import com.timelordtty.mydca.data.dto.TodayTodoDto
import com.timelordtty.mydca.data.dto.FinanceRadarDto
import com.timelordtty.mydca.data.dto.UpdateDraftRequestDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Android 侧 WealthHub API 边界。
 * 只声明查看、预览和用户手动确认相关接口；移动端不直接写数据库。
 */
interface WealthHubApi {
    @GET("api/v2/risk-watch-rules")
    suspend fun riskRules(@Query("page") page: Int = 0, @Query("size") size: Int = 20): List<com.timelordtty.mydca.data.dto.RiskRuleDto>

    @GET("api/v2/risk-watch-rules/{id}/snapshots")
    suspend fun riskSnapshots(@Path("id") id: String, @Query("page") page: Int = 0, @Query("size") size: Int = 20): List<com.timelordtty.mydca.data.dto.RiskSnapshotDto>

    @GET("api/v2/risk-watch-rules/{id}/events")
    suspend fun riskEvents(@Path("id") id: String, @Query("page") page: Int = 0, @Query("size") size: Int = 20): List<com.timelordtty.mydca.data.dto.RiskEventDto>

    @GET("api/v2/research-plans")
    suspend fun researchPlans(@Query("page") page: Int = 0, @Query("size") size: Int = 20): List<com.timelordtty.mydca.data.dto.ResearchPlanDto>

    @GET("api/v2/research-plans/{id}")
    suspend fun researchPlan(@Path("id") id: String): com.timelordtty.mydca.data.dto.ResearchPlanDto

    @GET("api/v2/backtest-lab/runs")
    suspend fun researchRuns(@Query("page") page: Int = 0, @Query("size") size: Int = 50): List<com.timelordtty.mydca.data.dto.ResearchRunDto>

    @GET("api/v2/finance-radar")
    suspend fun getFinanceRadar(): FinanceRadarDto

    @GET("api/v2/backtest-lab/recent")
    suspend fun recentBacktests(): List<com.timelordtty.mydca.data.dto.BacktestResultDto>
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

    @GET("api/v2/drafts/{draftId}/history")
    suspend fun draftHistory(@Path("draftId") draftId: Long): List<DraftLifecycleEventDto>

    @POST("api/v2/drafts/{draftId}/reopen")
    suspend fun reopenDraft(@Path("draftId") draftId: Long): DraftLedgerEntryDto

    @POST("api/v2/drafts/{draftId}/copy")
    suspend fun copyConfirmedDraft(@Path("draftId") draftId: Long): DraftLedgerEntryDto

    @PUT("api/v2/drafts/{draftId}")
    suspend fun updateDraft(
        @Path("draftId") draftId: Long,
        @Body request: UpdateDraftRequestDto,
    ): DraftLedgerEntryDto

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

    /** 产品主数据只读列表，仅用于投资草稿让主人明确选择真实产品。 */
    @GET("api/v2/products")
    suspend fun getProducts(
        @Query("keyword") keyword: String? = null,
        @Query("assetType") assetType: String? = null,
        @Query("channel") channel: String? = null,
    ): List<ProductDto>

    @GET("api/v2/mobile/overview")
    suspend fun getMobileOverview(): MobileOverviewDto

    @GET("api/v2/mobile/accounts")
    suspend fun getMobileAccounts(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): MobilePageDto<MobileAccountDto>

    @GET("api/v2/mobile/accounts/{accountId}")
    suspend fun getMobileAccountDetail(@Path("accountId") accountId: Long): MobileAccountDto

    @GET("api/v2/mobile/cash-flow")
    suspend fun getMobileCashFlow(): MobileCashFlowDto

    @GET("api/v2/mobile/transactions")
    suspend fun getMobileTransactions(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): MobilePageDto<MobileTransactionDto>

    @GET("api/v2/mobile/holdings")
    suspend fun getMobileHoldings(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): MobilePageDto<MobileHoldingDto>

    /** 指定产品在各账户的真实持仓来源，供卖出 / 赎回草稿选择持仓来源账户（只读）。 */
    @GET("api/v2/holdings/product/{productId}/by-account")
    suspend fun getProductHoldingsByAccount(@Path("productId") productId: Long): List<MobileHoldingByAccountDto>

    /** 待结算订单列表（只读）。列表本身不会 preview，也不会 confirm。 */
    @GET("api/v2/settlements/pending")
    suspend fun getPendingSettlements(): List<PendingSettlementOrderDto>

    @GET("api/v2/settlements/history")
    suspend fun getSettlementHistory(): List<SettlementAuditDto>

    @GET("api/v2/settlements/history/{orderId}")
    suspend fun getSettlementAudit(@Path("orderId") orderId: String): SettlementAuditDto

    /**
     * 人工结算只读预览：返回现金 / 持仓 / 手续费影响与 freshPreviewToken。
     * 该接口不会写 settlement_confirm / ledger_txn，也不会改动订单状态。
     */
    @POST("api/v2/settlements/preview")
    suspend fun previewSettlement(@Body request: SettlementPreviewRequestDto): SettlementPreviewDto

    /**
     * 人工结算确认（真实生成内部账本）。必须携带 preview 返回的 freshPreviewToken，
     * 后端会重新计算并比对指纹，任何关键变化都会阻断本次确认。
     */
    @POST("api/v2/settlements/confirm")
    suspend fun confirmSettlement(@Body request: SettlementPreviewRequestDto): SettlementConfirmDto
}
