package com.timelordtty.mydca.ui.screens

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation

@Composable
fun LoginPlaceholderScreen() {
    PageScaffold {
        LoginPlaceholderContent()
    }
}

@Composable
fun LoginPlaceholderContent() {
    var baseUrl by remember { mutableStateOf("http://10.0.2.2:8080/") }
    var token by remember { mutableStateOf("") }

    SafetyBanner("首版只保留登录与 Token 配置占位。Token 后续必须进入安全存储，不允许硬编码到源码。")
    SectionCard(
        title = "接口配置",
        description = "用于开发环境联调；生产地址和真实 Token 不应提交到仓库。",
    ) {
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("BaseUrl") },
            supportingText = { Text("示例值仅用于 Android 模拟器访问本机后端") },
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Bearer Token 占位") },
            supportingText = { Text("当前不持久化；后续接入登录后改为安全存储") },
            visualTransformation = PasswordVisualTransformation(),
        )
    }
}
