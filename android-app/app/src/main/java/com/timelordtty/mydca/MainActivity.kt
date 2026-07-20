package com.timelordtty.mydca

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.timelordtty.mydca.ui.MyDcaApp
import com.timelordtty.mydca.notification.NotificationNavigationTarget
import com.timelordtty.mydca.notification.NotificationCandidateStore

/**
 * Android 原生 App 启动入口。
 *
 * 首版只承接移动端查看和确认体验，不直接写数据库、不自动确认草稿。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationCandidateStore.initialize(this)
        acceptNavigationTarget(intent)
        setContent {
            MyDcaApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        acceptNavigationTarget(intent)
    }

    private fun acceptNavigationTarget(intent: Intent?) {
        NotificationNavigationTarget.set(intent?.getStringExtra(NotificationNavigationTarget.EXTRA_CANDIDATE_ID))
    }
}
