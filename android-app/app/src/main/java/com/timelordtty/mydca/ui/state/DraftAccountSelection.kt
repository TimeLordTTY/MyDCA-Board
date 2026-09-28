package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.data.dto.MobileAccountDto

/**
 * 草稿账户选择的前端提示规则。
 *
 * 普通消费（EXPENSE）只允许后端标记为可支出的真实叶子账户；父账户、RESERVED、INVESTABLE
 * 与待分配账户都不作为消费来源。转账（TRANSFER）可能是主人主动做资金分区，
 * SPENDABLE / RESERVED / INVESTABLE 之间都允许转移，前端不按资金用途阻断。
 * 投资（BUY / SUBSCRIPTION）只允许 INVESTABLE 真实叶子账户；只有国债逆回购（BOND_REPO）
 * 才允许使用 RESERVED 专款，且仍必须是真实叶子账户。
 * 卖出 / 赎回（SELL / REDEMPTION）的到账账户必须是真实叶子账户、非 POSITION 持仓账户且币种与
 * 产品一致；持仓来源由持仓接口返回，不在这里按资金用途过滤。
 *
 * 这里只负责前端提示和过滤，最终安全边界仍由后端 preview / confirm 重新校验，
 * 移动端不会代替后端放行。
 */
object DraftAccountSelection {
    const val EXPENSE = "EXPENSE"
    const val INCOME = "INCOME"
    const val TRANSFER = "TRANSFER"
    const val BUY = "BUY"
    const val SUBSCRIPTION = "SUBSCRIPTION"
    const val SELL = "SELL"
    const val REDEMPTION = "REDEMPTION"

    private const val SPENDABLE = "SPENDABLE"
    private const val RESERVED = "RESERVED"
    private const val INVESTABLE = "INVESTABLE"
    private const val POSITION = "POSITION"

    /** 按交易类型返回当前可选择的真实账户，规则与后端草稿安全规则保持一致。 */
    fun selectableFor(
        txnType: String?,
        accounts: List<MobileAccountDto>,
        productAssetType: String? = null,
        productCurrency: String? = null,
    ): List<MobileAccountDto> {
        return accounts.filter { account -> isSelectable(txnType, account, productAssetType, productCurrency) }
    }

    /** 判断单个账户在当前交易类型下是否可以选择。 */
    fun isSelectable(
        txnType: String?,
        account: MobileAccountDto,
        productAssetType: String? = null,
        productCurrency: String? = null,
    ): Boolean {
        if (!account.selectableForDraft) return false
        return when (normalizeType(txnType)) {
            EXPENSE -> isEligibleExpenseAccount(account)
            BUY, SUBSCRIPTION -> isEligibleInvestmentAccount(account, productAssetType)
            SELL, REDEMPTION -> isEligibleSellRedeemTarget(account, productCurrency)
            else -> true
        }
    }

    /**
     * 卖出 / 赎回的到账账户必须是真实叶子账户，且不能是 POSITION 持仓账户；
     * 产品币种已知时还要求币种一致。持仓来源由持仓接口单独返回，不走这里过滤。
     */
    fun isEligibleSellRedeemTarget(account: MobileAccountDto, productCurrency: String? = null): Boolean {
        if (!account.leaf || !account.selectableForDraft) return false
        if (account.accountType.equals(POSITION, ignoreCase = true)) return false
        val targetCurrency = account.currency?.trim().orEmpty()
        val wantedCurrency = productCurrency?.trim().orEmpty()
        if (targetCurrency.isEmpty() || wantedCurrency.isEmpty()) return true
        return targetCurrency.equals(wantedCurrency, ignoreCase = true)
    }

    /**
     * 投资买入 / 申购的资金来源必须是 INVESTABLE 真实叶子账户；
     * 国债逆回购（BOND_REPO）允许使用 RESERVED 专款，其它产品不得动用专款。
     */
    fun isEligibleInvestmentAccount(account: MobileAccountDto, productAssetType: String? = null): Boolean {
        if (!account.leaf || !account.selectableForDraft) return false
        if (account.fundUsage.equals(INVESTABLE, ignoreCase = true)) return true
        return isBondRepo(productAssetType) && account.fundUsage.equals(RESERVED, ignoreCase = true)
    }

    /** 普通消费必须同时满足：叶子账户、草稿可选、消费可选、资金用途为 SPENDABLE。 */
    fun isEligibleExpenseAccount(account: MobileAccountDto): Boolean {
        return account.leaf &&
            account.selectableForDraft &&
            account.selectableForExpense &&
            account.fundUsage.equals(SPENDABLE, ignoreCase = true)
    }

    /** 返回该账户不能用于当前交易类型的原因；可以正常选择时返回 null。 */
    fun rejectionReason(
        txnType: String?,
        account: MobileAccountDto,
        productAssetType: String? = null,
        productCurrency: String? = null,
    ): String? {
        if (!account.selectableForDraft) {
            return if (account.leaf) {
                "该账户当前不可用于草稿记账。"
            } else {
                "父账户只做只读聚合，不能作为记账账户。"
            }
        }
        return when (normalizeType(txnType)) {
            EXPENSE -> expenseRejectionReason(account)
            BUY, SUBSCRIPTION -> investmentRejectionReason(account, productAssetType)
            SELL, REDEMPTION -> sellRedeemRejectionReason(account, productCurrency)
            else -> null
        }
    }

    private fun sellRedeemRejectionReason(account: MobileAccountDto, productCurrency: String?): String? {
        if (isEligibleSellRedeemTarget(account, productCurrency)) return null
        return when {
            !account.leaf -> "父账户只做只读聚合，不能作为到账账户。"
            account.accountType.equals(POSITION, ignoreCase = true) ->
                "POSITION 持仓账户不能作为到账账户，请选择现金类 REAL 叶子账户。"
            else -> "到账账户币种必须与产品币种一致，请重新选择。"
        }
    }

    private fun expenseRejectionReason(account: MobileAccountDto): String? {
        if (isEligibleExpenseAccount(account)) return null
        return when {
            !account.leaf -> "父账户只做只读聚合，不能作为日常消费账户。"
            account.fundUsage.equals(RESERVED, ignoreCase = true) -> "专款 RESERVED 不得用于日常消费。"
            account.fundUsage.equals(INVESTABLE, ignoreCase = true) -> "可投资 INVESTABLE 不得用于日常消费。"
            account.fundUsage.isNullOrBlank() -> "待分配账户未完成资金分区，不得直接用于日常消费。"
            else -> "普通消费只允许 SPENDABLE 叶子账户。"
        }
    }

    private fun investmentRejectionReason(account: MobileAccountDto, productAssetType: String?): String? {
        if (isEligibleInvestmentAccount(account, productAssetType)) return null
        return when {
            !account.leaf -> "父账户只做只读聚合，不能作为投资资金来源账户。"
            account.fundUsage.equals(SPENDABLE, ignoreCase = true) -> "日常消费 SPENDABLE 账户不得用于投资买入 / 申购。"
            account.fundUsage.equals(RESERVED, ignoreCase = true) ->
                "专款 RESERVED 仅允许用于国债逆回购，不得用于普通买入 / 申购。"
            account.fundUsage.isNullOrBlank() -> "待分配账户未完成资金分区，不得直接用于投资。"
            else -> "投资买入 / 申购只允许 INVESTABLE 真实叶子账户。"
        }
    }

    private fun isBondRepo(productAssetType: String?): Boolean =
        productAssetType?.trim()?.equals("BOND_REPO", ignoreCase = true) == true

    /**
     * 转账的前端提示：转出 / 转入不能相同，币种不一致时提前提示。
     *
     * 这里只是提示，不阻断保存；最终是否可转以后端 preview / confirm 为准。
     */
    fun transferValidationMessage(source: MobileAccountDto?, target: MobileAccountDto?): String? {
        if (source == null || target == null) return null
        if (source.id == target.id) {
            return "转出账户与转入账户不能相同，请重新选择转入账户。"
        }
        val sourceCurrency = source.currency?.trim().orEmpty()
        val targetCurrency = target.currency?.trim().orEmpty()
        if (sourceCurrency.isNotEmpty() && targetCurrency.isNotEmpty() &&
            !sourceCurrency.equals(targetCurrency, ignoreCase = true)
        ) {
            return "转出账户与转入账户币种不一致，跨币种转账暂不支持；最终以后端预览校验为准。"
        }
        return null
    }

    private fun normalizeType(txnType: String?): String = txnType.orEmpty().trim().uppercase()
}
