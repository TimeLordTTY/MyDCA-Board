package com.timelordtty.mydca

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.IntentCompat
import com.timelordtty.mydca.ui.MyDcaApp
import com.timelordtty.mydca.notification.NotificationNavigationTarget
import com.timelordtty.mydca.notification.NotificationCandidateStore
import com.timelordtty.mydca.share.ExternalSharePendingStore
import com.timelordtty.mydca.share.ExternalShareRequest
import com.timelordtty.mydca.share.ExternalShareResolver

/**
 * Android 原生 App 启动入口。
 *
 * 首版只承接移动端查看和确认体验，不直接写数据库、不自动确认草稿。
 * 系统 Share Sheet 送进来的内容只登记为进程内一次性 pending share（只预填，不解析、不建档）。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationCandidateStore.initialize(this)
        acceptNavigationTarget(intent)
        acceptExternalShare(intent)
        setContent {
            MyDcaApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        acceptNavigationTarget(intent)
        acceptExternalShare(intent)
    }

    private fun acceptNavigationTarget(intent: Intent?) {
        NotificationNavigationTarget.set(intent?.getStringExtra(NotificationNavigationTarget.EXTRA_CANDIDATE_ID))
    }

    /**
     * 只读取 action / type / EXTRA_TEXT / EXTRA_STREAM 四个字段并交给纯解析层，
     * 由解析层决定“能不能收”；这里不解析记账候选、不创建草稿、不发起任何网络请求。
     *
     * 分享原文与图片 URI 既不打印也不持久化；读取失败时按“不支持”处理，只留中文提示。
     */
    private fun acceptExternalShare(intent: Intent?) {
        if (intent == null) return
        val request = runCatching {
            ExternalShareRequest(
                action = intent.action,
                mimeType = intent.type,
                sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
                sharedUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.toString(),
            )
        }.getOrElse {
            ExternalShareRequest(action = intent.action)
        }
        ExternalSharePendingStore.instance.publish(ExternalShareResolver.resolve(request))
    }
}
