package com.fetchpro.downloadmanager.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fetchpro.downloadmanager.domain.model.DownloadItem
import com.fetchpro.downloadmanager.domain.model.DownloadState

/**
 * White Soft 3D Card - implements Design 7 selected by user
 * ACTIVE (3) / QUEUED / COMPLETED tabs, floating blue progress, soft shadows
 */
@Composable
fun Soft3DDownloadCard(
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = Color(0xFF2563EB).copy(alpha = 0.1f)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // File icon with 3D effect
                Card(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFDBEAFE))
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "${formatBytes(item.downloadedBytes)} / ${item.totalBytes?.let { formatBytes(it) } ?: "?"} • ${formatSpeed(item.speedBytesPerSecond)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${item.downloadedBytes} / ${item.totalBytes ?: 0} MB • ${item.progressPercent}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                }

                // Percentage badge
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (item.state) {
                            DownloadState.COMPLETED -> Color(0xFFDCFCE7)
                            DownloadState.FAILED -> Color(0xFFFEE2E2)
                            else -> Color(0xFFDBEAFE)
                        }
                    )
                ) {
                    Text(
                        text = "${item.progressPercent}%",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = when (item.state) {
                            DownloadState.COMPLETED -> Color(0xFF16A34A)
                            DownloadState.FAILED -> Color(0xFFDC2626)
                            else -> Color(0xFF2563EB)
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 3D Progress bar - thick blue with soft shadow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(Color(0xFFF1F5F9), RoundedCornerShape(4.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(item.progress)
                        .background(Color(0xFF2563EB), RoundedCornerShape(4.dp))
                        .shadow(4.dp, RoundedCornerShape(4.dp), spotColor = Color(0xFF2563EB))
                )
            }

            Spacer(Modifier.height(12.dp))

            // Action buttons with 3D soft style
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (item.state) {
                    DownloadState.DOWNLOADING -> {
                        FilledTonalButton(
                            onClick = onPause,
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFF1F5F9)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pause", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onCancel,
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    DownloadState.PAUSED -> {
                        Button(
                            onClick = onResume,
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Resume")
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(20.dp)) {
                            Text("Cancel")
                        }
                        if (onRefresh != null) {
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = onRefresh, shape = RoundedCornerShape(20.dp)) { Text("Refresh") }
                        }
                    }
                    DownloadState.QUEUED -> {
                        AssistChip(
                            onClick = {},
                            label = { Text("Queued • ${formatBytes(item.totalBytes ?: 0)}") },
                            leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(20.dp)) { Text("Cancel") }
                    }
                    DownloadState.COMPLETED -> {
                        Button(
                            onClick = onOpen,
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                        ) {
                            Text("OPEN")
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Completed • ${formatBytes(item.downloadedBytes)} • Oct 26, 11:30 AM",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B)
                        )
                    }
                    DownloadState.FAILED -> {
                        Button(onClick = onRetry, shape = RoundedCornerShape(20.dp)) { Text("Retry") }
                        Spacer(Modifier.width(8.dp))
                        if (onRefresh != null) {
                            OutlinedButton(onClick = onRefresh, shape = RoundedCornerShape(20.dp)) { Text("Refresh") }
                            Spacer(Modifier.width(8.dp))
                        }
                        OutlinedButton(onClick = onDelete, shape = RoundedCornerShape(20.dp)) { Text("Remove") }
                    }
                    DownloadState.CANCELLED -> {
                        OutlinedButton(onClick = onDelete, shape = RoundedCornerShape(20.dp)) { Text("Remove") }
                    }
                }
            }
        }
    }
}
