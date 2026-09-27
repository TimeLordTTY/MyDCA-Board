package com.timelordtty.mydca.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.timelordtty.mydca.ui.QuickCaptureAction
import com.timelordtty.mydca.ui.QuickCaptureEntry
import com.timelordtty.mydca.ui.QuickCaptureHub

/**
 * 快速记账面板。
 *
 * 只提供“去哪里采集”的四个入口与数量提示，不承载任何 parse / preview / confirm 按钮，
 * 因此面板本身不可能产生正式入账动作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCaptureSheet(
    entries: List<QuickCaptureEntry>,
    onSelect: (QuickCaptureAction) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = QuickCaptureHub.PANEL_TITLE,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = QuickCaptureHub.PANEL_DESCRIPTION,
                style = MaterialTheme.typography.bodyMedium,
            )
            entries.forEach { entry ->
                QuickCaptureEntryCard(entry = entry, onClick = { onSelect(entry.action) })
            }
        }
    }
}

@Composable
private fun QuickCaptureEntryCard(
    entry: QuickCaptureEntry,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = entry.action.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = entry.action.description,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            entry.badge?.let { badge ->
                StatusPill(badge.text)
            }
        }
    }
}
