package com.fetchpro.downloadmanager.presentation.ui.screens.sponsor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fetchpro.downloadmanager.download.sponsorblock.SponsorBlockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SponsorBlockUiState(
    val enabled: Boolean = false,
    val categories: Set<String> = setOf("sponsor", "intro", "outro"),
    val availableCategories: List<String> = listOf("sponsor", "intro", "outro", "interaction", "selfpromo", "music_offtopic"),
    val testVideoId: String = "dQw4w9WgXcQ",
    val segments: List<SponsorBlockManager.SponsorSegment> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class SponsorBlockViewModel @Inject constructor(
    private val sponsorBlockManager: SponsorBlockManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SponsorBlockUiState())
    val uiState: StateFlow<SponsorBlockUiState> = _uiState.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(enabled = enabled)
    }

    fun toggleCategory(category: String) {
        val current = _uiState.value.categories.toMutableSet()
        if (category in current) current.remove(category) else current.add(category)
        _uiState.value = _uiState.value.copy(categories = current)
    }

    fun setTestVideoId(id: String) {
        _uiState.value = _uiState.value.copy(testVideoId = id)
    }

    fun fetchSegments() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val segments = sponsorBlockManager.getSegments(_uiState.value.testVideoId)
            _uiState.value = _uiState.value.copy(segments = segments, isLoading = false)
        }
    }

    fun getFfmpegFilter(): String? {
        return sponsorBlockManager.generateFfmpegFilter(_uiState.value.segments)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SponsorBlockScreen(viewModel: SponsorBlockViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("SponsorBlock (Seal)") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Skip sponsor segments automatically using SponsorBlock API", style = MaterialTheme.typography.bodyMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Enable SponsorBlock")
                        Switch(checked = uiState.enabled, onCheckedChange = { viewModel.setEnabled(it) })
                    }
                    Text("Categories to skip:", style = MaterialTheme.typography.labelMedium)
                    uiState.availableCategories.forEach { cat ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(cat)
                            Checkbox(checked = cat in uiState.categories, onCheckedChange = { viewModel.toggleCategory(cat) })
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Test: Fetch segments for video", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(value = uiState.testVideoId, onValueChange = { viewModel.setTestVideoId(it) }, label = { Text("Video ID (e.g. dQw4w9WgXcQ)") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { viewModel.fetchSegments() }, enabled = !uiState.isLoading) {
                        if (uiState.isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp)) else Text("Fetch Segments")
                    }
                    if (uiState.segments.isNotEmpty()) {
                        Text("Found ${uiState.segments.size} segments:", style = MaterialTheme.typography.labelMedium)
                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                            items(uiState.segments) { seg ->
                                Text("${seg.category}: ${seg.segment.first}s - ${seg.segment.second}s (${seg.uuid.take(8)})", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text("FFmpeg filter:", style = MaterialTheme.typography.labelMedium)
                        Text(viewModel.getFfmpegFilter() ?: "None", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("How it works:", style = MaterialTheme.typography.labelMedium)
                    Text("1. Enter YouTube video ID\n2. Fetch segments from sponsor.ajay.app\n3. Generate ffmpeg filter to skip\n4. When downloading video, ffmpeg will cut sponsor parts automatically", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
