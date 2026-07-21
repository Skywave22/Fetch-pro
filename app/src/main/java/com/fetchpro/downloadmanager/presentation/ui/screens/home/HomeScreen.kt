package com.fetchpro.downloadmanager.presentation.ui.screens.home

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.fetchpro.downloadmanager.domain.model.DownloadCategory
import com.fetchpro.downloadmanager.domain.model.DownloadSortBy
import com.fetchpro.downloadmanager.domain.model.DownloadTimeFilter
import com.fetchpro.downloadmanager.presentation.ui.components.DownloadItemCard
import com.fetchpro.downloadmanager.presentation.ui.components.NotificationPermissionHandler
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    initialUrl: String? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showBatchMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFromUri(uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) viewModel.exportToUri(uri)
    }

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) {
            viewModel.onUrlInputChanged(initialUrl)
        }
    }

    NotificationPermissionHandler {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("FetchPro") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    actions = {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                        }
                        IconButton(onClick = { showBatchMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Batch")
                        }
                        DropdownMenu(expanded = showBatchMenu, onDismissRequest = { showBatchMenu = false }) {
                            DropdownMenuItem(text = { Text("Pause All") }, onClick = { viewModel.pauseAll(); showBatchMenu = false })
                            DropdownMenuItem(text = { Text("Resume All") }, onClick = { viewModel.resumeAll(); showBatchMenu = false })
                            DropdownMenuItem(text = { Text("Cancel All") }, onClick = { viewModel.cancelAll(); showBatchMenu = false })
                            DropdownMenuItem(text = { Text("Delete Completed") }, onClick = { viewModel.deleteCompleted(); showBatchMenu = false })
                            Divider()
                            DropdownMenuItem(text = { Text("Import links from .txt") }, onClick = { importLauncher.launch(arrayOf("text/plain")); showBatchMenu = false })
                            DropdownMenuItem(text = { Text("Export links to .txt") }, onClick = { exportLauncher.launch("fetchpro_links.txt"); showBatchMenu = false })
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            Text("Sort by:", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelMedium)
                            DownloadSortBy.values().forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(sort.name.replace("_", " ")) },
                                    onClick = { viewModel.setSort(sort); showSortMenu = false }
                                )
                            }
                            Divider()
                            Text("Time:", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelMedium)
                            DownloadTimeFilter.values().forEach { tf ->
                                DropdownMenuItem(
                                    text = { Text(tf.name) },
                                    onClick = { viewModel.setTimeFilter(tf); showSortMenu = false }
                                )
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { viewModel.addDownload() }) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                // Smart clipboard banner (1DM+ feature)
                uiState.clipboardDetectedUrl?.let { detected ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Link detected in clipboard:", style = MaterialTheme.typography.labelMedium)
                            Text(detected, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { viewModel.consumeClipboardUrl() }) { Text("Download") }
                                OutlinedButton(onClick = { viewModel.dismissClipboard() }) { Text("Dismiss") }
                            }
                        }
                    }
                }

                // Input area
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = uiState.urlInput,
                            onValueChange = viewModel::onUrlInputChanged,
                            label = { Text("Paste URL to download") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    clipboard.getText()?.text?.let { viewModel.onUrlInputChanged(it) }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                                }
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { viewModel.addDownload() },
                                enabled = !uiState.isAdding && uiState.urlInput.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (uiState.isAdding) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text("Download")
                            }
                            OutlinedButton(onClick = { viewModel.setSmartClipboard(!uiState.smartClipboardEnabled) }) {
                                Text(if (uiState.smartClipboardEnabled) "Smart ON" else "Smart OFF")
                            }
                        }
                    }
                }

                // Category chips (1DM+ categorization)
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DownloadCategory.values()) { cat ->
                        FilterChip(
                            selected = uiState.category == cat,
                            onClick = { viewModel.setCategory(cat) },
                            label = { Text(cat.name) }
                        )
                    }
                }

                uiState.error?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = err, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.clearError() }) { Text("Dismiss") }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.filteredDownloads.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            Text("No downloads yet", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Paste a link above to start • ${uiState.downloads.size} total",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Text(
                        "${uiState.filteredDownloads.size} of ${uiState.downloads.size} • Sorted by ${uiState.sortBy.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.filteredDownloads, key = { it.id }) { item ->
                            DownloadItemCard(
                                item = item,
                                onPause = { viewModel.pause(item.id) },
                                onResume = { viewModel.resume(item.id) },
                                onCancel = { viewModel.cancel(item.id) },
                                onDelete = { viewModel.delete(item.id) },
                                onOpen = {
                                    val file = File(item.filePath)
                                    if (file.exists()) {
                                        try {
                                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                            val mime = item.mimeType ?: "*/*"
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, mime)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Open with"))
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "No app to open file: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onRetry = { viewModel.retry(item.id) },
                                onRefresh = { viewModel.refreshLink(item.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}
