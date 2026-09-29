package com.timelordtty.mydca.ui

/** Radar choices only identify existing screens. No destination represents a write action. */
enum class FinanceRadarDestination {
    DRAFTS, OUTBOX, SETTLEMENTS, ASSETS;
}

data class FinanceRadarNavigation(
    val route: AppRoute,
    val focusOutbox: Boolean = false,
    val openSettlements: Boolean = false,
)

fun FinanceRadarDestination.navigation(): FinanceRadarNavigation = when (this) {
    FinanceRadarDestination.DRAFTS -> FinanceRadarNavigation(AppRoute.Drafts)
    FinanceRadarDestination.OUTBOX -> FinanceRadarNavigation(AppRoute.Drafts, focusOutbox = true)
    FinanceRadarDestination.SETTLEMENTS -> FinanceRadarNavigation(AppRoute.TodayTodo, openSettlements = true)
    FinanceRadarDestination.ASSETS -> FinanceRadarNavigation(AppRoute.Accounts)
}
