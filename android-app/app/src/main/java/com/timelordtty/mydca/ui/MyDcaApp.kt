package com.timelordtty.mydca.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.timelordtty.mydca.core.design.MyDcaTheme
import com.timelordtty.mydca.ui.screens.DraftInboxScreen
import com.timelordtty.mydca.ui.screens.LoginPlaceholderScreen
import com.timelordtty.mydca.ui.screens.OverviewScreen
import com.timelordtty.mydca.ui.screens.PlaceholderScreen
import com.timelordtty.mydca.ui.screens.SettingsScreen
import com.timelordtty.mydca.ui.screens.TodayTodoScreen

/**
 * MyDCA Android 首版应用壳。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDcaApp() {
    MyDcaTheme {
        var currentRoute by rememberSaveable { mutableStateOf(AppRoute.TodayTodo) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(currentRoute.title)
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    AppRoute.entries.forEach { route ->
                        NavigationBarItem(
                            selected = currentRoute == route,
                            onClick = { currentRoute = route },
                            label = { Text(route.navLabel) },
                            icon = { Text(route.navLabel.take(1)) },
                        )
                    }
                }
            }
        ) { innerPadding ->
            Crossfade(
                targetState = currentRoute,
                label = "screen-crossfade",
                modifier = Modifier.padding(innerPadding),
            ) { route ->
                when (route) {
                    AppRoute.Login -> LoginPlaceholderScreen()
                    AppRoute.Overview -> OverviewScreen()
                    AppRoute.TodayTodo -> TodayTodoScreen()
                    AppRoute.Drafts -> DraftInboxScreen()
                    AppRoute.Accounts -> PlaceholderScreen(
                        title = "账户 / 流水 / 持仓",
                        description = "移动端首版只保留查看入口，后续按账户、流水、持仓拆分只读列表。",
                    )
                    AppRoute.Settings -> SettingsScreen()
                }
            }
        }
    }
}
