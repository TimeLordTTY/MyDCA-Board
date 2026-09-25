package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.data.dto.MobileAccountDto

/**
 * 草稿账户选择的前端提示规则。
 *
 * 普通消费（EXPENSE）只允许后端标记为可支出的真实叶子账户；父账户、RESERVED、INVESTABLE
 * 与待分配账户都不作为消费来源。这里只负责前端提示和过滤，最终安全边界仍由后端
 * preview / confirm 重新校验，移动端不会代替后端放行。
 */
object DraftAccountSelection {
    const val EXPENSE = "EXPENSE"
    const val INCOME = "INCOME"

    private const val SPENDABLE = "SPENDABLE"
    private const val RESERVED = "RESERVED"
    private const val INVESTABLE = "INVESTABLE"

    /** 按交易类型返回当前可选择的真实账户，规则与后端草稿安全规则保持一致。 */
    fun selectableFor(txnType: String?, accounts: List<MobileAccountDto>): List<MobileAccountDto> {
        return accounts.filter { account -> isSelectable(txnType, account) }
    }

    /** 判断单个账户在当前交易类型下是否可以选择。 */
    fun isSelectable(txnType: String?, account: MobileAccountDto): Boolean {
        if (!account.selectableForDraft) return false
        return normalizeType(txnType) != EXPENSE || isEligibleExpenseAccount(account)
    }

    /** 普通消费必须同时满足：叶子账户、草稿可选、消费可选、资金用途为 SPENDABLE。 */
    fun isEligibleExpenseAccount(account: MobileAccountDto): Boolean {
        return account.leaf &&
            account.selectableForDraft &&
            account.selectableForExpense &&
            account.fundUsage.equals(SPENDABLE, ignoreCase = true)
    }

    /** 返回该账户不能用于当前交易类型的原因；可以正常选择时返回 null。 */
    fun rejectionReason(txnType: String?, account: MobileAccountDto): String? {
        if (!account.selectableForDraft) {
            return if (account.leaf) {
                "该账户当前不可用于草稿记账。"
            } else {
                "父账户只做只读聚合，不能作为记账账户。"
            }
        }
        if (normalizeType(txnType) != EXPENSE || isEligibleExpenseAccount(account)) {
            return null
        }
        return when {
            !account.leaf -> "父账户只做只读聚合，不能作为日常消费账户。"
            account.fundUsage.equals(RESERVED, ignoreCase = true) -> "专款 RESERVED 不得用于日常消费。"
            account.fundUsage.equals(INVESTABLE, ignoreCase = true) -> "可投资 INVESTABLE 不得用于日常消费。"
            account.fundUsage.isNullOrBlank() -> "待分配账户未完成资金分区，不得直接用于日常消费。"
            else -> "普通消费只允许 SPENDABLE 叶子账户。"
        }
    }

    private fun normalizeType(txnType: String?): String = txnType.orEmpty().trim().uppercase()
}
