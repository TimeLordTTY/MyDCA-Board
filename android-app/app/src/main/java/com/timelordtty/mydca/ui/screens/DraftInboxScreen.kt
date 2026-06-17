package com.timelordtty.mydca.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DraftInboxScreen() {
    var showConfirmDialog by remember { mutableStateOf(false) }
    val confirmSupported = false

    PageScaffold {
        SafetyBanner("草稿箱只承接查看、预览和用户手动确认。确认按钮必须等待后端 preview.confirmSupported=true。")
        SectionCard(
            title = "待确认草稿",
            description = "首版使用空状态骨架；接入 GET /api/v2/drafts 后展示 DRAFT 草稿列表。",
        ) {
            StatusPill("DRAFT 列表占位")
        }
        SectionCard(
            title = "草稿预览",
            description = "后续调用 POST /api/v2/drafts/{draftId}/preview，并展示账户、金额和影响提示。",
        ) {
            KeyValueRow("确认支持", if (confirmSupported) "可确认" else "未生成可确认预览")
            KeyValueRow("正式入账", "必须二次确认")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { /* 后续接入 preview 接口 */ }) {
                    Text("生成预览")
                }
                Button(
                    enabled = confirmSupported,
                    onClick = { showConfirmDialog = true },
                ) {
                    Text("确认记账")
                }
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("确认正式记账？") },
            text = { Text("只有在后端预览显示可确认时，才允许调用 confirm 接口。") },
            confirmButton = {
                Button(onClick = { showConfirmDialog = false }) {
                    Text("确认")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}
