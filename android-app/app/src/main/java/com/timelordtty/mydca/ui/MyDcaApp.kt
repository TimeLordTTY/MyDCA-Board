package com.timelordtty.mydca.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import com.timelordtty.mydca.data.repository.WealthRepository
import com.timelordtty.mydca.ui.screens.AssetsScreen
import com.timelordtty.mydca.ui.screens.DraftInboxScreen
import com.timelordtty.mydca.ui.screens.ExternalShareNoticeBanner
import com.timelordtty.mydca.ui.screens.OverviewScreen
import com.timelordtty.mydca.ui.screens.SettingsScreen
import com.timelordtty.mydca.ui.screens.TodayTodoScreen
import com.timelordtty.mydca.ui.screens.LoginScreen
import com.timelordtty.mydca.ui.screens.OcrEntryMode
import com.timelordtty.mydca.ui.screens.OcrDraftScreen
import com.timelordtty.mydca.ui.screens.QuickCaptureSheet
import com.timelordtty.mydca.ui.state.AccountFundUsageFilter
import com.timelordtty.mydca.notification.NotificationCandidateStore
import com.timelordtty.mydca.notification.NotificationNavigationTarget
import com.timelordtty.mydca.outbox.AndroidDraftOutbox
import com.timelordtty.mydca.outbox.DraftOutboxOrigin
import com.timelordtty.mydca.share.ExternalSharePendingStore
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
        val shareNotice by ExternalSharePendingStore.instance.notice.collectAsState()
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

        Box(modifier = Modifier.fillMaxSize()) {
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

            shareNotice?.let { message ->
                ExternalShareNoticeBanner(
                    message = message,
                    onDismiss = { ExternalSharePendingStore.instance.consumeNotice() },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
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
        val context = LocalContext.current
        var currentRoute by rememberSaveable { mutableStateOf(AppRoute.TodayTodo) }
        var selectedDraftId by rememberSaveable { mutableStateOf<Long?>(null) }
        var accountFilterValue by rememberSaveable { mutableStateOf(AccountFundUsageFilter.ALL.name) }
        var draftEntryMode by remember { mutableStateOf<OcrEntryMode?>(null) }
        var quickFocus by remember { mutableStateOf(QuickCaptureFocus.NONE) }
        var isQuickCaptureOpen by rememberSaveable { mutableStateOf(false) }
        val shareSession = remember { ExternalShareCaptureSession() }
        val todoRepository = remember(services.wealthHubApi) { TodoRepository(services.wealthHubApi) }
        val draftRepository = remember(services.wealthHubApi) { DraftRepository(services.wealthHubApi) }
        val aiAccountingRepository = remember(services.wealthHubApi) { AiAccountingRepository(services.wealthHubApi) }
        val wealthRepository = remember(services.wealthHubApi) { WealthRepository(services.wealthHubApi) }
        val outboxScope = rememberCoroutineScope()
        val draftOutbox = remember(context) {
            AndroidDraftOutbox.createQueue(context) { entry, draftId ->
                if (entry.origin == DraftOutboxOrigin.PAYMENT_NOTIFICATION) {
                    NotificationCandidateStore.markDraftCreated(entry.sourceRef, draftId)
                }
            }
        }
        LaunchedEffect(draftOutbox, aiAccountingRepository) {
            draftOutbox.retryDueEntries(aiAccountingRepository)
        }
        LifecycleEventEffect(Lifecycle.Event.ON_START) {
            outboxScope.launch { draftOutbox.retryDueEntries(aiAccountingRepository) }
        }
        val notificationTarget by NotificationNavigationTarget.candidateId.collectAsState()
        LaunchedEffect(notificationTarget) {
            if (notificationTarget != null) {
                currentRoute = AppRoute.TodayTodo
                draftEntryMode = null
                quickFocus = QuickCaptureFocus.NONE
                shareSession.onManualNavigation()
            }
        }
        val pendingShare by ExternalSharePendingStore.instance.pending.collectAsState()
        val sharedCapture by shareSession.active.collectAsState()
        LaunchedEffect(pendingShare) {
            // 一次性消费外部分享：本效果只在已登录分支内运行，因此未登录时会保留到登录后再消费一次。
            val capture = pendingShare ?: return@LaunchedEffect
            ExternalSharePendingStore.instance.consume()
            val destination = ExternalShareHub.destinationFor(capture.payload)
            shareSession.adopt(capture)
            selectedDraftId = null
            NotificationNavigationTarget.clear()
            quickFocus = QuickCaptureFocus.NONE
            draftEntryMode = destination.entryMode
            currentRoute = destination.route
        }
        val notificationCandidates by NotificationCandidateStore.candidates.collectAsState()
        val outboxEntries by draftOutbox.entries.collectAsState()
        val quickCaptureEntries = QuickCaptureHub.entries(
            pendingCandidateCount = QuickCaptureCounts.pendingCandidates(notificationCandidates),
            outboxCount = QuickCaptureCounts.outboxRetries(outboxEntries),
        )

        /** 快速面板只做导航：不经手解析、草稿创建、预览或确认。 */
        fun startQuickCapture(action: QuickCaptureAction) {
            val decision = QuickCaptureHub.decide(action)
            if (decision.clearSelectedDraftId) selectedDraftId = null
            if (decision.clearSelectedCandidateId) NotificationNavigationTarget.clear()
            shareSession.onCaptureExit()
            draftEntryMode = decision.entryMode
            quickFocus = decision.focus
            currentRoute = decision.route
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(currentRoute.title)
                    }
                )
            },
            floatingActionButton = {
                if (QuickCaptureHub.isEntryVisibleOn(currentRoute, isSubFlowOpen = draftEntryMode != null)) {
                    ExtendedFloatingActionButton(onClick = { isQuickCaptureOpen = true }) {
                        Text("记一笔")
                    }
                }
            },
            bottomBar = {
                NavigationBar {
                    AppRoute.entries.forEach { route ->
                        NavigationBarItem(
                            selected = currentRoute == route,
                            onClick = {
                                currentRoute = route
                                draftEntryMode = null
                                quickFocus = QuickCaptureHub.focusAfterManualNavigation()
                                NotificationNavigationTarget.clear()
                                shareSession.onManualNavigation()
                            },
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
                    AppRoute.Overview -> OverviewScreen(
                        wealthRepository = wealthRepository,
                        apiConfigError = apiConfigError,
                    )
                    AppRoute.TodayTodo -> TodayTodoScreen(
                        todoRepository = todoRepository,
                        aiAccountingRepository = aiAccountingRepository,
                        apiConfigError = apiConfigError,
                        selectedCandidateId = notificationTarget,
                        onOpenDraft = { draftId ->
                            selectedDraftId = draftId
                            currentRoute = AppRoute.Drafts
                            quickFocus = QuickCaptureFocus.NONE
                        },
                        draftOutbox = draftOutbox,
                        focusCandidates = quickFocus.notificationCandidates,
                    )
                    AppRoute.Drafts -> {
                        val entryMode = draftEntryMode
                        if (entryMode != null) {
                            OcrDraftScreen(
                                repository = aiAccountingRepository,
                                entryMode = entryMode,
                                onClose = {
                                    draftEntryMode = null
                                    shareSession.onCaptureExit()
                                },
                                onOpenDraft = { draftId ->
                                    selectedDraftId = draftId
                                    draftEntryMode = null
                                    shareSession.onCaptureExit()
                                },
                                draftOutbox = draftOutbox,
                                externalShare = sharedCapture,
                                onExternalShareSettled = { shareSession.onDraftCreated() },
                            )
                        } else {
                            DraftInboxScreen(
                                draftRepository = draftRepository,
                                wealthRepository = wealthRepository,
                                apiConfigError = apiConfigError,
                                selectedDraftId = selectedDraftId,
                                onDraftHandled = { selectedDraftId = null },
                                onOpenImageOcr = { draftEntryMode = OcrEntryMode.Image },
                                onOpenManualEntry = { draftEntryMode = OcrEntryMode.ManualText },
                                onOpenDraft = { draftId -> selectedDraftId = draftId },
                                draftOutbox = draftOutbox,
                                draftCreationGateway = aiAccountingRepository,
                                focusOutbox = quickFocus.outbox,
                            )
                        }
                    }
                    AppRoute.Accounts -> AssetsScreen(
                        wealthRepository = wealthRepository,
                        apiConfigError = apiConfigError,
                        selectedFilter = AccountFundUsageFilter.fromSavedValue(accountFilterValue),
                        onFilterChange = { accountFilterValue = it.name },
                    )
                    AppRoute.Settings -> SettingsScreen(
                        baseUrl = baseUrl,
                        displayName = displayName,
                        apiConfigError = apiConfigError,
                        onBaseUrlChange = onBaseUrlChange,
                        onLogout = onLogout,
                    )
                }
            }
        }

        if (isQuickCaptureOpen) {
            QuickCaptureSheet(
                entries = quickCaptureEntries,
                onSelect = { action ->
                    isQuickCaptureOpen = false
                    startQuickCapture(action)
                },
                onDismiss = { isQuickCaptureOpen = false },
            )
        }
}
