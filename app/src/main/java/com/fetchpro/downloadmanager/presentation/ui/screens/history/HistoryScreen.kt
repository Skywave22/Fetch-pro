package com.fetchpro.downloadmanager.presentation.ui.screens.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val history by viewModel.history.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("History") }) }) { padding ->
        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No completed downloads yet")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(item.fileName, style = MaterialTheme.typography.titleMedium)
                            Text(item.filePath, style = MaterialTheme.typography.bodySmall)
                            Text("Size: ${item.totalBytes ?: item.downloadedBytes} bytes")
                            Text("Completed: ${java.util.Date(item.completedAt ?: item.updatedAt)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
