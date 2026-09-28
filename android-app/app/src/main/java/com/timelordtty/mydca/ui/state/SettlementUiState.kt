package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.dto.PendingSettlementOrderDto
import com.timelordtty.mydca.data.dto.SettlementPreviewDto
import com.timelordtty.mydca.data.repository.SettlementRepository
import com.timelordtty.mydca.data.repository.WealthRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 待结算页状态。
 *
 * isLoading 只在首次加载、页面还没有任何数据时为 true；刷新已有数据时使用 isRefreshing，
 * 避免刷新过程中清空主人正在查看的待结算清单。
 */
data class PendingSettlementUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val orders: List<PendingSettlementOrderDto> = emptyList(),
    /** productId -> 产品名称；只用于列表展示，不参与任何结算计算。 */
    val productNames: Map<Long, String> = emptyMap(),
    val errorMessage: String? = null,
    val lastUpdatedAt: Long? = null,
)

/**
 * 单笔订单的结算预览状态。
 *
 * inputFingerprint 记录「生成这份预览时的结算输入」：表单任何字段变化都会使旧预览失效，
 * 从而禁用确认按钮，避免主人拿旧预览直接结算。
 */
data class SettlementPreviewUiState(
    val preview: SettlementPreviewDto? = null,
    val inputFingerprint: String? = null,
    val isGenerating: Boolean = false,
    val errorMessage: String? = null,
    val lastGeneratedAt: Long? = null,
) {
    val hasPreview: Boolean
        get() = preview != null
}

/**
 * 待结算数据装配。
 *
 * 只读取待结算订单与产品名称，不做任何 preview 或 confirm；刷新失败只记录 errorMessage
 * 并保留上一次成功数据，只有首次加载失败才进入空错误态。
 */
class SettlementStateHolder(
    private val repository: SettlementRepository,
    private val wealthRepository: WealthRepository? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun loadPending(
        previous: PendingSettlementUiState = PendingSettlementUiState(isLoading = false),
    ): PendingSettlementUiState = coroutineScope {
        val ordersDeferred = async { repository.listPendingSettlements() }
        val productsDeferred = async { wealthRepository?.getProducts() }

        val ordersResult = ordersDeferred.await()
        val productsResult = productsDeferred.await()
        val productNames = (productsResult as? NetworkResult.Success)?.data
            ?.mapNotNull { product -> product.productName?.takeIf { it.isNotBlank() }?.let { product.id to it } }
            ?.toMap()
            ?: previous.productNames

        when (ordersResult) {
            is NetworkResult.Success -> previous.copy(
                isLoading = false,
                isRefreshing = false,
                orders = ordersResult.data,
                productNames = productNames,
                errorMessage = null,
                lastUpdatedAt = clock(),
            )
            is NetworkResult.Failure -> previous.copy(
                isLoading = false,
                isRefreshing = false,
                productNames = productNames,
                errorMessage = ordersResult.message,
            )
        }
    }
}