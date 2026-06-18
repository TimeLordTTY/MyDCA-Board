package com.timelordtty.mydca.ui.screens

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.timelordtty.mydca.core.network.ApiConfig

@Composable
fun LoginPlaceholderScreen() {
    var baseUrl by remember { mutableStateOf(ApiConfig.DEFAULT_LOCAL_BASE_URL) }
    var token by remember { mutableStateOf("") }

    PageScaffold {
        LoginPlaceholderContent(
            baseUrl = baseUrl,
            token = token,
            onBaseUrlChange = { baseUrl = it },
            onTokenChange = { token = it },
        )
    }
}

@Composable
fun LoginPlaceholderContent(
    baseUrl: String,
    token: String,
    onBaseUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
) {
    SafetyBanner("首版只保留登录与 Token 配置占位。Token 后续必须进入安全存储，不允许硬编码到源码。")
    SectionCard(
        title = "接口配置",
        description = "用于开发环境联调；生产地址和真实 Token 不应提交到仓库。当前输入只保存在内存，应用重启后会恢复默认值。",
    ) {
        OutlinedTextField(
            value = baseUrl,
            onValueChange = onBaseUrlChange,
            label = { Text("BaseUrl") },
            supportingText = { Text("示例值仅用于 Android 模拟器访问本机后端") },
        )
        OutlinedTextField(
            value = token,
            onValueChange = onTokenChange,
            label = { Text("Bearer Token 占位") },
            supportingText = { Text("当前不持久化、不打印；后续接入登录后改为安全存储") },
            visualTransformation = PasswordVisualTransformation(),
        )
    }
}
