package com.fetchpro.downloadmanager.presentation.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fetchpro.downloadmanager.domain.model.DownloadItem
import com.fetchpro.downloadmanager.domain.model.DownloadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadItemCard(
    item: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(targetValue = item.progress, label = "progress")

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StatusChip(state = item.state)
            }

            Spacer(Modifier.height(12.dp))

            if (item.state == DownloadState.DOWNLOADING || item.state == DownloadState.PAUSED || item.state == DownloadState.QUEUED) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${item.progressPercent}% • ${formatBytes(item.downloadedBytes)} / ${item.totalBytes?.let { formatBytes(it) } ?: "?"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (item.speedBytesPerSecond > 0 && item.state == DownloadState.DOWNLOADING) {
                        Text(
                            text = formatSpeed(item.speedBytesPerSecond),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (item.state == DownloadState.FAILED) {
                Text(
                    text = item.errorMessage ?: "Download failed",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (item.state) {
                    DownloadState.DOWNLOADING -> {
                        FilledTonalButton(onClick = onPause) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pause")
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel")
                        }
                    }
                    DownloadState.PAUSED -> {
                        Button(onClick = onResume) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Resume")
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = onCancel) {
                            Text("Cancel")
                        }
                        if (onRefresh != null) {
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = onRefresh) { Text("Refresh") }
                        }
                    }
                    DownloadState.QUEUED -> {
                        Text("Queued...", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = onCancel) { Text("Cancel") }
                    }
                    DownloadState.COMPLETED -> {
                        Button(onClick = onOpen) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Open")
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = onDelete) { Text("Delete") }
                    }
                    DownloadState.FAILED -> {
                        Button(onClick = onRetry) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Retry")
                        }
                        Spacer(Modifier.width(8.dp))
                        if (onRefresh != null) {
                            OutlinedButton(onClick = onRefresh) { Text("Refresh") }
                            Spacer(Modifier.width(8.dp))
                        }
                        OutlinedButton(onClick = onDelete) { Text("Remove") }
                    }
                    DownloadState.CANCELLED -> {
                        OutlinedButton(onClick = onDelete) { Text("Remove") }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(state: DownloadState) {
    val (color, text) = when (state) {
        DownloadState.QUEUED -> MaterialTheme.colorScheme.secondary to "Queued"
        DownloadState.DOWNLOADING -> MaterialTheme.colorScheme.primary to "Downloading"
        DownloadState.PAUSED -> MaterialTheme.colorScheme.tertiary to "Paused"
        DownloadState.COMPLETED -> ColorSchemeKeySuccess to "Completed"
        DownloadState.FAILED -> MaterialTheme.colorScheme.error to "Failed"
        DownloadState.CANCELLED -> MaterialTheme.colorScheme.outline to "Cancelled"
    }
    AssistChip(
        onClick = {},
        label = { Text(text) },
        colors = AssistChipDefaults.assistChipColors(labelColor = color)
    )
}

private val ColorSchemeKeySuccess = androidx.compose.ui.graphics.Color(0xFF2E7D32)

fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format("%.2f GB", gb)
        mb >= 1 -> String.format("%.1f MB", mb)
        kb >= 1 -> String.format("%.0f KB", kb)
        else -> "$bytes B"
    }
}

fun formatSpeed(bps: Long): String {
    if (bps <= 0) return "0 B/s"
    val kb = bps / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1 -> String.format("%.1f MB/s", mb)
        kb >= 1 -> String.format("%.0f KB/s", kb)
        else -> "$bps B/s"
    }
}
