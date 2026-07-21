package com.fetchpro.downloadmanager.presentation.ui.screens.profiles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fetchpro.downloadmanager.domain.model.SiteProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class SiteProfilesUiState(
    val profiles: List<SiteProfile> = emptyList(),
    val hostInput: String = "",
    val userAgentInput: String = "",
    val headersInput: String = ""
)

@HiltViewModel
class SiteProfilesViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(SiteProfilesUiState())
    val uiState: StateFlow<SiteProfilesUiState> = _uiState.asStateFlow()

    // In production, this would be backed by Room/DataStore
    private val profiles = mutableListOf<SiteProfile>()

    init {
        // Mock data for demo
        profiles.addAll(
            listOf(
                SiteProfile(hostPattern = "*.example.com", userAgent = "Mozilla/5.0 FetchPro", customHeaders = mapOf("Referer" to "https://example.com")),
                SiteProfile(hostPattern = "private.site.com", customHeaders = mapOf("Authorization" to "Bearer token"))
            )
        )
        _uiState.value = _uiState.value.copy(profiles = profiles)
    }

    fun onHostChange(v: String) { _uiState.value = _uiState.value.copy(hostInput = v) }
    fun onUserAgentChange(v: String) { _uiState.value = _uiState.value.copy(userAgentInput = v) }
    fun onHeadersChange(v: String) { _uiState.value = _uiState.value.copy(headersInput = v) }

    fun addProfile() {
        val host = _uiState.value.hostInput.trim()
        if (host.isBlank()) return
        val headers = _uiState.value.headersInput.split("\n")
            .mapNotNull {
                val parts = it.split(":", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
            }.toMap()

        val profile = SiteProfile(
            id = UUID.randomUUID().toString(),
            hostPattern = host,
            userAgent = _uiState.value.userAgentInput.takeIf { it.isNotBlank() },
            customHeaders = headers
        )
        profiles.add(profile)
        _uiState.value = _uiState.value.copy(profiles = profiles.toList(), hostInput = "", userAgentInput = "", headersInput = "")
    }

    fun deleteProfile(id: String) {
        profiles.removeAll { it.id == id }
        _uiState.value = _uiState.value.copy(profiles = profiles.toList())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteProfilesScreen(viewModel: SiteProfilesViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Site Profiles (ADM)") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Advanced profiles for sites – custom headers, User-Agent, proxy per site", style = MaterialTheme.typography.bodyMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add Profile", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(value = uiState.hostInput, onValueChange = { viewModel.onHostChange(it) }, label = { Text("Host pattern e.g. *.example.com") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = uiState.userAgentInput, onValueChange = { viewModel.onUserAgentChange(it) }, label = { Text("User-Agent (optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = uiState.headersInput, onValueChange = { viewModel.onHeadersChange(it) }, label = { Text("Headers (one per line: Key: Value)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Button(onClick = { viewModel.addProfile() }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add Profile")
                    }
                }
            }

            Text("Saved Profiles (${uiState.profiles.size})", style = MaterialTheme.typography.titleSmall)

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.profiles) { profile ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(profile.hostPattern, style = MaterialTheme.typography.titleSmall)
                                IconButton(onClick = { viewModel.deleteProfile(profile.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                            profile.userAgent?.let { Text("UA: $it", style = MaterialTheme.typography.bodySmall) }
                            if (profile.customHeaders.isNotEmpty()) {
                                Text("Headers: ${profile.customHeaders}", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }
}
