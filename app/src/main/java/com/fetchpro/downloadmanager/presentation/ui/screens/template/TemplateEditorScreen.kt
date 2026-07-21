package com.fetchpro.downloadmanager.presentation.ui.screens.template

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.fetchpro.downloadmanager.domain.model.CustomFilenameTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class TemplateUiState(
    val currentTemplate: String = "%(title)s.%(ext)s",
    val preview: String = "My Video Title.mp4",
    val variables: Map<String, String> = mapOf(
        "%(title)s" to "My Video Title",
        "%(id)s" to "abc123",
        "%(ext)s" to "mp4",
        "%(uploader)s" to "ChannelName",
        "%(resolution)s" to "1080p"
    )
)

@HiltViewModel
class TemplateEditorViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(TemplateUiState())
    val uiState: StateFlow<TemplateUiState> = _uiState.asStateFlow()

    fun setTemplate(template: String) {
        val preview = CustomFilenameTemplate(template).apply(_uiState.value.variables)
        _uiState.value = _uiState.value.copy(currentTemplate = template, preview = preview)
    }

    fun insertVariable(variable: String) {
        val current = _uiState.value.currentTemplate
        setTemplate(current + variable)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(viewModel: TemplateEditorViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Filename Template (Seal)") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Custom filename template like yt-dlp / Seal", style = MaterialTheme.typography.bodyMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Template:", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = uiState.currentTemplate,
                        onValueChange = { viewModel.setTemplate(it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        minLines = 2
                    )
                    Text("Preview: ${uiState.preview}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }

            Text("Presets:", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CustomFilenameTemplate.PRESETS) { preset ->
                    FilterChip(
                        selected = uiState.currentTemplate == preset,
                        onClick = { viewModel.setTemplate(preset) },
                        label = { Text(preset, maxLines = 1) }
                    )
                }
            }

            Text("Variables:", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.variables.keys.toList()) { variable ->
                    AssistChip(
                        onClick = { viewModel.insertVariable(variable) },
                        label = { Text(variable) }
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Available variables:", style = MaterialTheme.typography.labelMedium)
                    uiState.variables.forEach { (k, v) ->
                        Text("$k = $v", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
