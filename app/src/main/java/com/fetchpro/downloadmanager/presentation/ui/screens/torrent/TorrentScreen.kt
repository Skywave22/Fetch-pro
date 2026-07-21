package com.fetchpro.downloadmanager.presentation.ui.screens.torrent

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fetchpro.downloadmanager.download.torrent.TorrentManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class TorrentUiState(
    val torrents: List<TorrentManager.TorrentDownloadInfo> = emptyList(),
    val magnetInput: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedTorrentFiles: List<com.fetchpro.downloadmanager.download.torrent.TorrentFileEntry> = emptyList(),
    val showFileSelection: Boolean = false
)

@HiltViewModel
class TorrentViewModel @Inject constructor(
    private val torrentManager: TorrentManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(TorrentUiState())
    val uiState: StateFlow<TorrentUiState> = _uiState.asStateFlow()

    fun onMagnetInputChange(value: String) {
        _uiState.value = _uiState.value.copy(magnetInput = value)
    }

    fun addTorrent(magnetOrFile: String, saveDir: File) {
        val trimmed = magnetOrFile.trim()
        if (trimmed.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Enter magnet link or .torrent file path")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                torrentManager.downloadTorrent(trimmed, saveDir).collect { info ->
                    val current = _uiState.value.torrents.toMutableList()
                    val idx = current.indexOfFirst { it.id == info.id }
                    if (idx >= 0) current[idx] = info else current.add(info)
                    _uiState.value = _uiState.value.copy(torrents = current, isLoading = false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }
        }
    }

    fun pauseTorrent(id: String) {
        torrentManager.pauseTorrent(id)
    }

    fun resumeTorrent(id: String) {
        torrentManager.resumeTorrent(id)
    }

    fun removeTorrent(id: String) {
        torrentManager.removeTorrent(id)
        _uiState.value = _uiState.value.copy(torrents = _uiState.value.torrents.filterNot { it.id == id })
    }

    fun showFileSelectionForTorrent(torrentId: String) {
        // Simulate file list from torrent metadata - in production, get from libtorrent handle
        val mockFiles = listOf(
            com.fetchpro.downloadmanager.download.torrent.TorrentFileEntry(0, "video/sample.mp4", "sample.mp4", 1024 * 1024 * 500),
            com.fetchpro.downloadmanager.download.torrent.TorrentFileEntry(1, "video/sample.srt", "sample.srt", 1024 * 50),
            com.fetchpro.downloadmanager.download.torrent.TorrentFileEntry(2, "docs/readme.txt", "readme.txt", 1024 * 5)
        )
        _uiState.value = _uiState.value.copy(selectedTorrentFiles = mockFiles, showFileSelection = true)
    }

    fun toggleFileSelection(index: Int) {
        val files = _uiState.value.selectedTorrentFiles.map { file ->
            if (file.index == index) file.copy(isSelected = !file.isSelected) else file
        }
        _uiState.value = _uiState.value.copy(selectedTorrentFiles = files)
    }

    fun setFilePriority(index: Int, priority: com.fetchpro.downloadmanager.download.torrent.FilePriority) {
        val files = _uiState.value.selectedTorrentFiles.map { file ->
            if (file.index == index) file.copy(priority = priority) else file
        }
        _uiState.value = _uiState.value.copy(selectedTorrentFiles = files)
    }

    fun hideFileSelection() {
        _uiState.value = _uiState.value.copy(showFileSelection = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentScreen(viewModel: TorrentViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            viewModel.onMagnetInputChange(it.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Torrent – 1DM+ Feature") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val dir = context.getExternalFilesDir("torrents") ?: File(context.filesDir, "torrents").apply { mkdirs() }
                viewModel.addTorrent(uiState.magnetInput, dir)
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Torrent")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Download Torrent files using magnet link, torrent URL or torrent file", style = MaterialTheme.typography.bodyMedium)

            OutlinedTextField(
                value = uiState.magnetInput,
                onValueChange = { viewModel.onMagnetInputChange(it) },
                label = { Text("Magnet link or .torrent file URI") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { filePicker.launch(arrayOf("application/x-bittorrent", "*/*")) }) {
                    Text("Pick .torrent file")
                }
                Button(onClick = {
                    val dir = context.getExternalFilesDir("torrents") ?: File(context.filesDir, "torrents").apply { mkdirs() }
                    viewModel.addTorrent(uiState.magnetInput, dir)
                }) {
                    Text("Start Download")
                }
            }

            uiState.error?.let { err ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(err, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.clearError() }) { Text("Dismiss") }
                    }
                }
            }

            if (uiState.torrents.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("No torrents yet – paste magnet link above", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.torrents) { torrent ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(torrent.name, style = MaterialTheme.typography.titleMedium)
                                Text("Progress: ${(torrent.progress * 100).toInt()}% • ${torrent.downloadedBytes}/${torrent.totalBytes}", style = MaterialTheme.typography.bodySmall)
                                Text("Peers: ${torrent.numPeers} Seeds: ${torrent.numSeeds} ↓ ${torrent.downloadRate} B/s ↑ ${torrent.uploadRate} B/s", style = MaterialTheme.typography.bodySmall)
                                Text("State: ${torrent.state} • ${torrent.savePath}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                LinearProgressIndicator(progress = { torrent.progress }, modifier = Modifier.fillMaxWidth())
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { viewModel.pauseTorrent(torrent.id) }) { Text("Pause") }
                                    OutlinedButton(onClick = { viewModel.resumeTorrent(torrent.id) }) { Text("Resume") }
                                    TextButton(onClick = { viewModel.removeTorrent(torrent.id) }) { Text("Remove") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
