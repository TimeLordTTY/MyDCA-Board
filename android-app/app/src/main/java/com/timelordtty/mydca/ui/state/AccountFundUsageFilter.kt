package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.data.dto.MobileAccountDto

enum class AccountFundUsageFilter(
    val label: String,
    val emptyMessage: String,
) {
    ALL("全部", "暂无账户"),
    SPENDABLE("可支出", "暂无可支出账户"),
    RESERVED("专款", "暂无专款账户"),
    INVESTABLE("可投资", "暂无可投资账户"),
    UNALLOCATED("待分配", "暂无待分配账户");

    fun matches(account: MobileAccountDto): Boolean = when (this) {
        ALL -> true
        UNALLOCATED -> account.fundUsage.isNullOrBlank()
        else -> account.fundUsage.equals(name, ignoreCase = true)
    }

    fun errorTitle(): String = if (this == ALL) "账户数据加载失败" else "${label}账户加载失败"

    companion object {
        fun fromSavedValue(value: String?): AccountFundUsageFilter =
            entries.firstOrNull { it.name == value } ?: ALL
    }
}
