package com.fetchpro.downloadmanager.presentation.ui.screens.home

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.fetchpro.downloadmanager.domain.model.DownloadSortBy
import com.fetchpro.downloadmanager.presentation.ui.components.Soft3DDownloadCard
import com.fetchpro.downloadmanager.presentation.ui.theme.WhiteSoft3DTheme
import java.io.File

/**
 * White Soft 3D Design - Selected by user (Design 7)
 * Matches image: white background, ACTIVE(3)/COMPLETED/QUEUED tabs, floating blue progress bars
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhiteSoft3DHomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    initialUrl: String? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var selectedTab by remember { mutableStateOf(0) } // 0=ACTIVE, 1=COMPLETED, 2=QUEUED
    var showSortMenu by remember { mutableStateOf(false) }
    var showBatchMenu by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFromUri(uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) viewModel.exportToUri(uri)
    }

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) viewModel.onUrlInputChanged(initialUrl)
    }

    WhiteSoft3DTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("3D DOWNLOADS", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text(
                                "${uiState.downloads.size} files • ${uiState.downloads.filter { it.state.name == "DOWNLOADING" }.size} active",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                    actions = {
                        IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Default.Sort, contentDescription = "Sort") }
                        IconButton(onClick = { showBatchMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }

                        DropdownMenu(expanded = showBatchMenu, onDismissRequest = { showBatchMenu = false }) {
                            DropdownMenuItem(text = { Text("Pause All") }, onClick = { viewModel.pauseAll(); showBatchMenu = false })
                            DropdownMenuItem(text = { Text("Resume All") }, onClick = { viewModel.resumeAll(); showBatchMenu = false })
                            DropdownMenuItem(text = { Text("Import .txt") }, onClick = { importLauncher.launch(arrayOf("text/plain")); showBatchMenu = false })
                            DropdownMenuItem(text = { Text("Export .txt") }, onClick = { exportLauncher.launch("fetchpro_links.txt"); showBatchMenu = false })
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            DownloadSortBy.values().forEach { sort ->
                                DropdownMenuItem(text = { Text(sort.name) }, onClick = { viewModel.setSort(sort); showSortMenu = false })
                            }
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    NavigationBarItem(selected = true, onClick = {}, icon = { Icon(Icons.Default.Add, contentDescription = null) }, label = { Text("Home") })
                    NavigationBarItem(selected = false, onClick = {}, icon = { Badge { Text("${uiState.downloads.size}") }; Icon(Icons.Default.Add, contentDescription = null) }, label = { Text("Downloads") })
                    NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Default.Add, contentDescription = null) }, label = { Text("Files") })
                    NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Default.Add, contentDescription = null) }, label = { Text("Settings") })
                }
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { viewModel.addDownload() }, containerColor = Color(0xFF2563EB)) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                }
            },
            containerColor = Color(0xFFF8FAFC)
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFFF8FAFC))
                    .padding(16.dp)
            ) {
                // ACTIVE / COMPLETED / QUEUED tabs like in image
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.White,
                    contentColor = Color(0xFF2563EB)
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("ACTIVE (${uiState.downloads.filter { it.state.name == "DOWNLOADING" }.size})") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("COMPLETED") })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("QUEUED") })
                }

                Spacer(Modifier.height(12.dp))

                // URL input (hidden in image but needed)
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(12.dp)) {
                        OutlinedTextField(
                            value = uiState.urlInput,
                            onValueChange = { viewModel.onUrlInputChanged(it) },
                            label = { Text("Paste URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                val filtered = when (selectedTab) {
                    0 -> uiState.filteredDownloads.filter { it.state.name == "DOWNLOADING" }
                    1 -> uiState.filteredDownloads.filter { it.state.name == "COMPLETED" }
                    2 -> uiState.filteredDownloads.filter { it.state.name == "QUEUED" || it.state.name == "PAUSED" }
                    else -> uiState.filteredDownloads
                }

                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No ${listOf("active", "completed", "queued")[selectedTab]} downloads", color = Color(0xFF64748B))
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(filtered, key = { it.id }) { item ->
                            Soft3DDownloadCard(
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
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, item.mimeType ?: "*/*")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Open"))
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "No app: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
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
