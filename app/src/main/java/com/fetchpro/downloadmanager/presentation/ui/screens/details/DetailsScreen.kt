package com.fetchpro.downloadmanager.presentation.ui.screens.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fetchpro.downloadmanager.presentation.ui.components.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    viewModel: DetailsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val item = uiState.item

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Details – 1DM+ Features") }, navigationIcon = {
                TextButton(onClick = onBack) { Text("Back") }
            })
        }
    ) { padding ->
        if (item == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(item.fileName, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text("URL: ${item.url}", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        Text("Path: ${item.filePath}", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        Text("Size: ${item.totalBytes?.let { formatBytes(it) } ?: "Unknown"}")
                        Text("Downloaded: ${formatBytes(item.downloadedBytes)}")
                        Text("State: ${item.state}")
                        Text("Resumable: ${item.supportsRange}")
                        Text("Parts: ${item.numParts}")
                        item.errorMessage?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.resume() }) { Text("Resume") }
                            OutlinedButton(onClick = { viewModel.pause() }) { Text("Pause") }
                            OutlinedButton(onClick = { viewModel.cancel() }) { Text("Cancel") }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Speed Limiter (1DM+)", style = MaterialTheme.typography.titleMedium)
                        Text(if (uiState.speedLimitKbps == 0L) "Unlimited" else "${uiState.speedLimitKbps} KB/s per-download")
                        Slider(
                            value = uiState.speedLimitKbps.toFloat(),
                            onValueChange = { viewModel.setSpeedLimit(it.toLong()) },
                            valueRange = 0f..5120f,
                            steps = 20
                        )
                        Text("0 = unlimited, up to 5 MB/s individual limit + global limit in Settings", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Scheduler (1DM+)", style = MaterialTheme.typography.titleMedium)
                        Text("Schedule this download for later", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.scheduleInMinutes(15) }) { Text("In 15m") }
                            OutlinedButton(onClick = { viewModel.scheduleInMinutes(60) }) { Text("In 1h") }
                            OutlinedButton(onClick = { viewModel.scheduleInMinutes(180) }) { Text("In 3h") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.scheduleWithWorkManager(30) }) { Text("WorkMgr 30m") }
                            TextButton(onClick = { viewModel.cancelSchedule() }) { Text("Cancel Schedule") }
                        }
                    }
                }
            }

            item {
                Text("Parts Detail (${item.numParts} parts up to 32 – 1DM+)", style = MaterialTheme.typography.titleMedium)
            }

            items(uiState.parts) { part ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Part #${part.index}: ${part.startByte} - ${if (part.endByte >=0) part.endByte else "EOF"}")
                        LinearProgressIndicator(
                            progress = {
                                if (part.length > 0) part.downloadedBytes.toFloat() / part.length else 0f
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                        Text("${formatBytes(part.downloadedBytes)} / ${if (part.length>0) formatBytes(part.length) else "Unknown"}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
