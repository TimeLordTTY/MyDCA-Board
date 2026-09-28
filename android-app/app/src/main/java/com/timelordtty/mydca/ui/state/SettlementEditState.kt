package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.data.dto.PendingSettlementOrderDto
import com.timelordtty.mydca.data.dto.SettlementPreviewDto
import com.timelordtty.mydca.data.dto.SettlementPreviewRequestDto
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Android 人工结算编辑表单（v0.13.0）。
 *
 * 字段按 orderType 动态启用：
 * - BUY / SUBSCRIPTION：确认日期、净值日期、实际净值、实际份额（可留空按金额 / 净值计算）、手续费；
 * - SELL / REDEMPTION：确认日期、净值日期、实际净值、实际份额、实际到账金额、手续费。
 *
 * 表单只描述「主人填了什么」。真正会不会落账由后端 preview.confirmSupported 与
 * freshPreviewToken 决定，移动端不会自行判断结算结果。
 */
data class SettlementEditForm(
    val orderId: String = "",
    val orderType: String = "",
    val productName: String = "",
    val productCode: String = "",
    val confirmDate: String = "",
    val navDate: String = "",
    val confirmNav: String = "",
    val confirmShares: String = "",
    val confirmAmount: String = "",
    val confirmFee: String = "",
    val note: String = "",
)

/** 结算请求构造结果；request 为空时 error 必须说明中文原因。 */
data class SettlementEditRequestResult(
    val request: SettlementPreviewRequestDto? = null,
    val error: String? = null,
) {
    val isValid: Boolean
        get() = request != null && error == null
}

/**
 * 人工结算的安全规则与中文文案，集中在此以便 UI 与单元测试复用。
 *
 * 关键边界：
 * - 不支持的类型一律不能生成预览或确认；
 * - 未生成 preview、preview 被阻断、缺少 freshPreviewToken，或预览生成后又修改了任意字段，都不能确认；
 * - 这里只做「输入格式与必填」校验，结算语义（现金 / 持仓 / 手续费）最终以后端 preview 为准。
 */
object SettlementEditState {
    /** 后端 confirm 支持的订单类型；四类投资订单之外一律阻断。 */
    val SUPPORTED_ORDER_TYPES = setOf("BUY", "SUBSCRIPTION", "SELL", "REDEMPTION")

    private val isoDatePattern = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val navPattern = Regex("""^-?\d+(\.\d{1,6})?$""")
    private val sharesPattern = Regex("""^-?\d+(\.\d{1,6})?$""")
    private val moneyPattern = Regex("""^-?\d+(\.\d{1,2})?$""")

    fun normalizeOrderType(orderType: String?): String? =
        orderType?.trim()?.uppercase()?.takeIf { it in SUPPORTED_ORDER_TYPES }

    fun isBuyLike(orderType: String?): Boolean =
        normalizeOrderType(orderType) in setOf("BUY", "SUBSCRIPTION")

    fun isSellLike(orderType: String?): Boolean =
        normalizeOrderType(orderType) in setOf("SELL", "REDEMPTION")

    /** 类型中文名，例如「买入 BUY」；未知类型明确提示待补充。 */
    fun orderTypeLabel(orderType: String?): String = when (normalizeOrderType(orderType)) {
        "BUY" -> "买入 BUY"
        "SUBSCRIPTION" -> "申购 SUBSCRIPTION"
        "SELL" -> "卖出 SELL"
        "REDEMPTION" -> "赎回 REDEMPTION"
        else -> orderType?.trim().orEmpty().ifBlank { "类型待补充" }
    }

    /** 动作中文名，用于按钮与弹窗标题。 */
    fun actionLabel(orderType: String?): String = when (normalizeOrderType(orderType)) {
        "BUY" -> "买入"
        "SUBSCRIPTION" -> "申购"
        "SELL" -> "卖出"
        "REDEMPTION" -> "赎回"
        else -> "结算"
    }

    /**
     * 四类订单的差异字段清单（只列需要主人手工填写的字段，顺序即界面顺序）。
     */
    fun fieldLabels(orderType: String?): List<String> = when {
        isBuyLike(orderType) -> listOf("确认日期", "净值日期", "实际净值", "实际份额", "手续费")
        isSellLike(orderType) -> listOf("确认日期", "净值日期", "实际净值", "实际份额", "实际到账金额", "手续费")
        else -> emptyList()
    }

    /**
     * 由待结算订单生成初始表单：确认日期 / 净值日期优先使用订单的预期日期，净值与份额留给主人填写。
     */
    fun formFromOrder(order: PendingSettlementOrderDto, today: String = ""): SettlementEditForm {
        val orderType = normalizeOrderType(order.orderType).orEmpty()
        val confirmDate = order.expectedConfirmDate?.trim().orEmpty()
        val navDate = order.expectedNavDate?.trim().orEmpty()
        return SettlementEditForm(
            orderId = order.orderId,
            orderType = orderType,
            confirmDate = if (confirmDate.isNotBlank()) confirmDate else today,
            navDate = if (navDate.isNotBlank()) navDate else if (confirmDate.isNotBlank()) confirmDate else today,
        )
    }

    /**
     * 校验并构造 preview / confirm 共用的请求体。
     *
     * confirmShares / confirmFee 允许留空：留空表示由后端按现有规则计算，明确输入 0 才会使用 0。
     */
    fun buildRequest(form: SettlementEditForm): SettlementEditRequestResult {
        if (form.orderId.isBlank()) {
            return SettlementEditRequestResult(error = "缺少订单号，无法结算。")
        }
        val orderType = normalizeOrderType(form.orderType)
            ?: return SettlementEditRequestResult(error = "该订单类型暂不支持人工结算。")

        val confirmDate = parseIsoDate(form.confirmDate)
            ?: return SettlementEditRequestResult(error = "请填写合法的确认日期（YYYY-MM-DD）。")
        val navDate = parseIsoDate(form.navDate)
            ?: return SettlementEditRequestResult(error = "请填写合法的净值日期（YYYY-MM-DD）。")

        val confirmNav = parseDecimal(form.confirmNav, navPattern)
            ?: return SettlementEditRequestResult(error = "请填写合法的实际净值（最多 6 位小数）。")
        if (confirmNav <= BigDecimal.ZERO) {
            return SettlementEditRequestResult(error = "实际净值必须大于 0。")
        }

        val confirmShares = if (form.confirmShares.isBlank()) {
            null
        } else {
            val parsed = parseDecimal(form.confirmShares, sharesPattern)
                ?: return SettlementEditRequestResult(error = "请填写合法的实际份额（最多 6 位小数）。")
            if (parsed <= BigDecimal.ZERO) {
                return SettlementEditRequestResult(error = "实际份额必须大于 0，留空则由后端按金额与净值计算。")
            }
            parsed
        }

        val confirmAmount = if (isSellLike(orderType)) {
            val parsed = parseDecimal(form.confirmAmount, moneyPattern)
                ?: return SettlementEditRequestResult(error = "卖出 / 赎回必须填写实际到账金额（最多 2 位小数）。")
            if (parsed <= BigDecimal.ZERO) {
                return SettlementEditRequestResult(error = "卖出 / 赎回的实际到账金额必须大于 0。")
            }
            parsed
        } else if (form.confirmAmount.isBlank()) {
            null
        } else {
            parseDecimal(form.confirmAmount, moneyPattern)
                ?: return SettlementEditRequestResult(error = "请填写合法的确认金额（最多 2 位小数）。")
        }

        val confirmFee = if (form.confirmFee.isBlank()) {
            null
        } else {
            val parsed = parseDecimal(form.confirmFee, moneyPattern)
                ?: return SettlementEditRequestResult(error = "请填写合法的手续费（最多 2 位小数）。")
            if (parsed < BigDecimal.ZERO) {
                return SettlementEditRequestResult(error = "手续费不能为负数。")
            }
            parsed
        }

        // 结算请求统一使用「不补零」的字符串口径，避免 10 与 10.00 生成两个不同的输入指纹。
        val request = SettlementPreviewRequestDto(
            orderId = form.orderId.trim(),
            confirmDate = confirmDate,
            navDate = navDate,
            confirmNav = confirmNav.stripTrailingZeros().toPlainString().toDouble(),
            confirmShares = confirmShares?.stripTrailingZeros()?.toPlainString()?.toDouble(),
            confirmAmount = confirmAmount?.stripTrailingZeros()?.toPlainString()?.toDouble(),
            confirmFee = confirmFee?.stripTrailingZeros()?.toPlainString()?.toDouble(),
            note = form.note.trim().ifBlank { null },
        )
        return SettlementEditRequestResult(request = request)
    }

    /**
     * 客户端输入指纹：只覆盖主人填写的结算输入。
     *
     * 后端 freshPreviewToken 才是权威令牌；这里只用于在界面层及时发现「预览生成后又改了字段」，
     * 从而立即禁用确认按钮并清空旧预览。
     */
    fun inputFingerprint(form: SettlementEditForm): String {
        val values = listOf(
            form.orderId.trim(),
            form.confirmDate.trim(),
            form.navDate.trim(),
            canonical(form.confirmNav),
            canonical(form.confirmShares),
            canonical(form.confirmAmount),
            canonical(form.confirmFee),
        )
        return values.joinToString("|")
    }

    /** 旧预览是否已失效：没有预览指纹，或表单字段与生成预览时不一致。 */
    fun isPreviewStale(form: SettlementEditForm, previewFingerprint: String?): Boolean =
        previewFingerprint == null || previewFingerprint != inputFingerprint(form)

    /**
     * 是否允许点击「确认结算」。
     *
     * 必须同时满足：类型受支持、表单合法、已有 preview、preview 属于当前订单、
     * preview.confirmSupported=true、携带非空 freshPreviewToken，且预览生成后字段未再修改。
     */
    fun canConfirm(
        form: SettlementEditForm,
        preview: SettlementPreviewDto?,
        previewFingerprint: String?,
        confirming: Boolean = false,
    ): Boolean {
        if (confirming) {
            return false
        }
        val orderType = normalizeOrderType(form.orderType) ?: return false
        if (preview == null) {
            return false
        }
        if (preview.orderId != form.orderId.trim()) {
            return false
        }
        if (preview.orderType != null && normalizeOrderType(preview.orderType) != orderType) {
            return false
        }
        if (!preview.confirmSupported || preview.freshPreviewToken.isNullOrBlank()) {
            return false
        }
        if (isPreviewStale(form, previewFingerprint)) {
            return false
        }
        return buildRequest(form).isValid
    }

    /** 预览被阻断时的中文原因，直接展示给主人。 */
    fun blockingText(preview: SettlementPreviewDto?): String {
        val reasons = preview?.blockingReasons.orEmpty().filter { it.isNotBlank() }
        return if (reasons.isEmpty()) {
            "结算预览未通过校验，请检查订单状态、资金来源与输入参数。"
        } else {
            reasons.joinToString("；")
        }
    }

    /** 只读结算预览的中文全文：先列输入，再逐条列出结算影响与提示。 */
    fun previewText(preview: SettlementPreviewDto?): String {
        if (preview == null) {
            return "尚未生成结算预览。"
        }
        val lines = mutableListOf<String>()
        lines += "订单：${preview.orderId} · ${preview.orderTypeLabel ?: orderTypeLabel(preview.orderType)}"
        if (!preview.productName.isNullOrBlank()) {
            val code = preview.productCode?.takeIf { it.isNotBlank() }
            lines += "产品：${preview.productName}${if (code != null) "（$code）" else ""}"
        }
        preview.confirmDate?.takeIf { it.isNotBlank() }?.let { lines += "确认日期：$it" }
        preview.navDate?.takeIf { it.isNotBlank() }?.let { lines += "净值日期：$it" }
        preview.confirmNav?.let { lines += "实际净值：${formatNumber(it)}" }
        preview.confirmShares?.let { lines += "实际份额：${formatNumber(it)}" }
        preview.confirmAmount?.let { lines += "实际到账金额：${formatNumber(it)}" }
        preview.computedShares?.let { lines += "按规则计算份额：${formatNumber(it)}" }
        preview.computedAmount?.let { lines += "按规则计算金额：${formatNumber(it)}" }
        preview.confirmFee?.let { lines += "手续费：${formatNumber(it)} ${preview.currency ?: ""}".trim() }
        val summary = preview.summaryLines.orEmpty().filter { it.isNotBlank() }
        if (summary.isNotEmpty()) {
            lines += "结算影响："
            summary.forEach { lines += "· $it" }
        }
        val warnings = preview.warnings.orEmpty().filter { it.isNotBlank() }
        warnings.forEach { lines += "提示：$it" }
        lines += impactFlagsText(preview)
        if (!preview.confirmSupported) {
            lines += "当前不可确认：${blockingText(preview)}"
        }
        return lines.joinToString("\n")
    }

    /** 二次确认弹窗标题：明确「确认结算【产品】买入 / 申购订单」。 */
    fun confirmDialogTitle(preview: SettlementPreviewDto?): String {
        if (preview == null) {
            return "确认结算？"
        }
        val action = actionLabel(preview.orderType)
        val product = preview.productName?.takeIf { it.isNotBlank() } ?: preview.orderId
        return "确认结算【$product】$action 订单？"
    }

    /** 二次确认弹窗正文：再次说明会真实生成内部账本与持仓 / 现金影响。 */
    fun confirmDialogMessage(preview: SettlementPreviewDto?, canConfirm: Boolean): String {
        if (!canConfirm || preview == null) {
            return "当前结算没有可确认的预览，移动端已阻止本次确认。"
        }
        val lines = mutableListOf<String>()
        lines += "确认后将调用后端结算接口，真正生成结算确认与内部账本分录。"
        if (preview.willChangeCash) {
            lines += "本次会改变相关账户现金余额。"
        }
        if (preview.willChangeHolding) {
            lines += "本次会改变相关持仓份额。"
        }
        val summary = preview.summaryLines.orEmpty().filter { it.isNotBlank() }
        if (summary.isNotEmpty()) {
            lines += summary.joinToString("\n")
        }
        lines += "本操作只在财富中枢内部记账，不会向券商 / 基金公司 / 交易所发起任何真实交易。"
        return lines.joinToString("\n")
    }

    /** 预览已失效时的中文提示。 */
    fun staleNotice(): String = "结算输入已修改，旧预览已失效，请重新生成结算预览。"

    /** 结算影响开关的中文摘要行。 */
    fun impactFlagsText(preview: SettlementPreviewDto): String {
        val parts = mutableListOf<String>()
        parts += if (preview.willCreateSettlementConfirm) "会写入结算确认" else "不写入结算确认"
        parts += if (preview.willCreateLedgerTxn) "会生成内部账本分录" else "不生成内部账本分录"
        parts += if (preview.willChangeCash) "会改变现金" else "不改变现金"
        parts += if (preview.willChangeHolding) "会改变持仓" else "不改变持仓"
        return parts.joinToString("；")
    }

    /** 待结算列表摘要：订单号 · 类型 · 产品 · 下单金额 / 份额 · 预计确认日期。 */
    fun orderSummary(order: PendingSettlementOrderDto, productName: String? = null): String {
        val parts = mutableListOf<String>()
        parts += order.orderId.ifBlank { "#${order.id}" }
        parts += orderTypeLabel(order.orderType)
        val product = productName?.takeIf { it.isNotBlank() } ?: order.productId?.let { "产品#$it" }
        if (product != null) {
            parts += product
        }
        when {
            order.shares != null -> parts += "份额 ${formatNumber(order.shares)}"
            order.amount != null -> parts += "金额 ${formatNumber(order.amount)}"
            else -> parts += "金额 / 份额待确认"
        }
        order.expectedConfirmDate?.takeIf { it.isNotBlank() }?.let { parts += "预计确认 $it" }
        return parts.joinToString(" · ")
    }

    /** 待结算条数，只统计状态为 PENDING 的订单。 */
    fun pendingCount(orders: List<PendingSettlementOrderDto>): Int = orders.count { isPending(it) }

    fun isPending(order: PendingSettlementOrderDto): Boolean =
        order.status?.trim()?.equals("PENDING", ignoreCase = true) == true

    fun formatNumber(value: Double?): String {
        if (value == null) {
            return "未知"
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    }

    private fun canonical(raw: String): String = raw.trim().toBigDecimalOrNull()
        ?.stripTrailingZeros()
        ?.toPlainString()
        ?: raw.trim()

    private fun parseIsoDate(raw: String): String? {
        val trimmed = raw.trim()
        if (!isoDatePattern.matches(trimmed)) {
            return null
        }
        return try {
            LocalDate.parse(trimmed).toString()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun parseDecimal(raw: String, pattern: Regex): BigDecimal? {
        val trimmed = raw.trim()
        if (!pattern.matches(trimmed)) {
            return null
        }
        return trimmed.toBigDecimalOrNull()?.stripTrailingZeros()
    }
}