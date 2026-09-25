package com.timelordtty.mydca.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun PageScaffold(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
fun SafetyBanner(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (!description.isNullOrBlank()) {
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
            content()
        }
    }
}

@Composable
fun StatusPill(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
fun KeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun EmptySection(title: String, message: String) {
    SectionCard(title = title, description = message) {
        StatusPill("暂无数据")
    }
}

@Composable
fun RetrySection(title: String, message: String, onRetry: () -> Unit) {
    SectionCard(title = title, description = message) {
        OutlinedButton(onClick = onRetry) {
            Text("重试")
        }
    }
}

/** 首次加载占位卡片，只在页面还没有任何数据时使用。 */
@Composable
fun LoadingSection(title: String) {
    SectionCard(title = title) {
        CircularProgressIndicator()
    }
}

/** 加载失败卡片，用于首次加载失败或完全没有可展示数据的情况。 */
@Composable
fun ErrorSection(title: String, message: String, onRetry: () -> Unit) {
    RetrySection(title = title, message = message, onRetry = onRetry)
}

/** 刷新操作条：展示最近一次成功刷新时间，并在刷新中禁用重复点击。 */
@Composable
fun RefreshBar(
    lastUpdatedAt: Long?,
    isRefreshing: Boolean,
    refreshLabel: String = "刷新",
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "最近更新：${formatClockTime(lastUpdatedAt)}",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = onRefresh, enabled = !isRefreshing) {
            Text(if (isRefreshing) "刷新中" else refreshLabel)
        }
    }
}

/** 保留既有内容的错误提示条：刷新失败时提示原因，但不隐藏上一次成功数据。 */
@Composable
fun NoticeBanner(title: String, message: String, onRetry: (() -> Unit)? = null) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(message, style = MaterialTheme.typography.bodySmall)
            onRetry?.let { retry ->
                OutlinedButton(onClick = retry) {
                    Text("重试")
                }
            }
        }
    }
}
