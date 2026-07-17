package com.timelordtty.mydca.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.timelordtty.mydca.auth.AuthSession
import com.timelordtty.mydca.auth.AuthState
import com.timelordtty.mydca.auth.KeystoreTokenStore
import com.timelordtty.mydca.core.design.MyDcaTheme
import com.timelordtty.mydca.core.network.ApiConfig
import com.timelordtty.mydca.core.network.NetworkModule
import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.repository.AuthRepository
import com.timelordtty.mydca.data.repository.AiAccountingRepository
import com.timelordtty.mydca.data.repository.DraftRepository
import com.timelordtty.mydca.data.repository.TodoRepository
import com.timelordtty.mydca.ui.screens.DraftInboxScreen
import com.timelordtty.mydca.ui.screens.OverviewScreen
import com.timelordtty.mydca.ui.screens.PlaceholderScreen
import com.timelordtty.mydca.ui.screens.SettingsScreen
import com.timelordtty.mydca.ui.screens.TodayTodoScreen
import com.timelordtty.mydca.ui.screens.LoginScreen
import com.timelordtty.mydca.ui.screens.OcrDraftScreen
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * MyDCA Android 应用壳：启动时先恢复 Keystore 会话，未登录时不会创建业务页面入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDcaApp() {
    MyDcaTheme {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var baseUrl by rememberSaveable { mutableStateOf(ApiConfig.DEFAULT_BASE_URL) }
        val authSession = remember { AuthSession(KeystoreTokenStore(context)) }
        val authState by authSession.state.collectAsState()
        LaunchedEffect(authSession) { authSession.restore() }

        val safeBaseUrl = baseUrl.ifBlank { ApiConfig.DEFAULT_BASE_URL }
        val servicesResult = remember(safeBaseUrl, authSession) {
            runCatching {
                NetworkModule.createServices(
                    config = ApiConfig(safeBaseUrl),
                    tokenProvider = authSession,
                    unauthorizedHandler = authSession,
                )
            }
        }
        val services = servicesResult.getOrNull()
        val authRepository = remember(services, authSession) {
            services?.let { AuthRepository(it.authApi, authSession) }
        }
        val apiConfigError = servicesResult.exceptionOrNull()?.message

        when (val state = authState) {
            AuthState.Initializing -> InitializingScreen()
            AuthState.Unauthenticated,
            is AuthState.AuthFailed,
            AuthState.Expired,
            AuthState.Authenticating -> LoginScreen(
                baseUrl = baseUrl,
                isAuthenticating = state == AuthState.Authenticating,
                errorMessage = when (state) {
                    is AuthState.AuthFailed -> state.message
                    AuthState.Expired -> "登录已失效，请重新登录"
                    else -> apiConfigError
                },
                onBaseUrlChange = { baseUrl = it },
                onLogin = { username, password ->
                    val repository = authRepository
                    if (repository == null) return@LoginScreen
                    scope.launch {
                        when (repository.login(username, password)) {
                            is NetworkResult.Success -> Unit
                            is NetworkResult.Failure -> Unit
                        }
                    }
                },
            )
            is AuthState.Authenticated -> {
                if (services == null) {
                    InvalidConfigurationScreen(
                        baseUrl = baseUrl,
                        message = apiConfigError ?: "接口配置无效",
                        onBaseUrlChange = { baseUrl = it },
                        onLogout = authSession::logout,
                    )
                } else {
                    AuthenticatedApp(
                        baseUrl = baseUrl,
                        displayName = state.displayName,
                        services = services,
                        apiConfigError = apiConfigError,
                        onBaseUrlChange = { baseUrl = it },
                        onLogout = { scope.launch { authRepository?.logout() ?: authSession.logout() } },
                    )
                }
            }
        }
    }
}

@Composable
private fun InitializingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("正在恢复安全会话…")
    }
}

@Composable
private fun InvalidConfigurationScreen(
    baseUrl: String,
    message: String,
    onBaseUrlChange: (String) -> Unit,
    onLogout: () -> Unit,
) {
    com.timelordtty.mydca.ui.screens.PageScaffold {
        com.timelordtty.mydca.ui.screens.SectionCard("接口配置需要修正", message) {
            androidx.compose.material3.OutlinedTextField(
                value = baseUrl,
                onValueChange = onBaseUrlChange,
                label = { Text("BaseUrl") },
            )
            androidx.compose.material3.OutlinedButton(onClick = onLogout) { Text("退出登录") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedApp(
    baseUrl: String,
    displayName: String?,
    services: NetworkModule.ApiServices,
    apiConfigError: String?,
    onBaseUrlChange: (String) -> Unit,
    onLogout: () -> Unit,
) {
        var currentRoute by rememberSaveable { mutableStateOf(AppRoute.TodayTodo) }
        var selectedDraftId by rememberSaveable { mutableStateOf<Long?>(null) }
        var showImageOcr by remember { mutableStateOf(false) }
        val todoRepository = remember(services.wealthHubApi) { TodoRepository(services.wealthHubApi) }
        val draftRepository = remember(services.wealthHubApi) { DraftRepository(services.wealthHubApi) }
        val aiAccountingRepository = remember(services.wealthHubApi) { AiAccountingRepository(services.wealthHubApi) }

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
                    AppRoute.Overview -> OverviewScreen()
                    AppRoute.TodayTodo -> TodayTodoScreen(
                        todoRepository = todoRepository,
                        apiConfigError = apiConfigError,
                        onOpenDraft = { draftId ->
                            selectedDraftId = draftId
                            currentRoute = AppRoute.Drafts
                        },
                    )
                    AppRoute.Drafts -> if (showImageOcr) {
                        OcrDraftScreen(
                            repository = aiAccountingRepository,
                            onClose = { showImageOcr = false },
                            onOpenDraft = { draftId ->
                                selectedDraftId = draftId
                                showImageOcr = false
                            },
                        )
                    } else {
                        DraftInboxScreen(
                            draftRepository = draftRepository,
                            apiConfigError = apiConfigError,
                            selectedDraftId = selectedDraftId,
                            onDraftHandled = { selectedDraftId = null },
                            onOpenImageOcr = { showImageOcr = true },
                        )
                    }
                    AppRoute.Accounts -> PlaceholderScreen(
                        title = "账户 / 流水 / 持仓",
                        description = "移动端首版只保留查看入口，后续按账户、流水、持仓拆分只读列表。",
                    )
                    AppRoute.Settings -> SettingsScreen(
                        baseUrl = baseUrl,
                        displayName = displayName,
                        apiConfigError = apiConfigError,
                        aiAccountingRepository = aiAccountingRepository,
                        onBaseUrlChange = onBaseUrlChange,
                        onLogout = onLogout,
                        onOpenDraft = { draftId ->
                            selectedDraftId = draftId
                            currentRoute = AppRoute.Drafts
                        },
                    )
                }
            }
        }
}
