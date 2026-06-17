package com.timelordtty.mydca

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.timelordtty.mydca.ui.MyDcaApp

/**
 * Android 原生 App 启动入口。
 *
 * 首版只承接移动端查看和确认体验，不直接写数据库、不自动确认草稿。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyDcaApp()
        }
    }
}
