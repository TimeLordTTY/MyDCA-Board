package com.timelordtty.mydca.ui

/**
 * 首版底部导航目标。
 */
enum class AppRoute(
    val title: String,
    val navLabel: String,
) {
    Overview("总览", "总览"),
    TodayTodo("今日待办", "待办"),
    Drafts("草稿箱", "草稿"),
    Accounts("账户 / 流水 / 持仓", "资产"),
    Settings("设置", "设置"),
}
