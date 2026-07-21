package com.fetchpro.downloadmanager.presentation.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fetchpro.downloadmanager.data.local.datastore.SettingsDataStore
import com.fetchpro.downloadmanager.domain.model.AppSettings
import com.fetchpro.downloadmanager.download.limiter.SpeedLimiterManager
import com.fetchpro.downloadmanager.download.proxy.ProxyConfig
import com.fetchpro.downloadmanager.download.proxy.ProxyManager
import com.fetchpro.downloadmanager.download.proxy.ProxyType
import com.fetchpro.downloadmanager.download.queue.DownloadQueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val queueManager: DownloadQueueManager,
    private val settingsDataStore: SettingsDataStore,
    private val speedLimiterManager: SpeedLimiterManager,
    private val proxyManager: ProxyManager
) : ViewModel() {
    val maxConcurrent: StateFlow<Int> = queueManager.maxConcurrent
    val settings: StateFlow<AppSettings> = settingsDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val proxyConfig: StateFlow<ProxyConfig> = proxyManager.proxyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProxyConfig())

    fun setMaxConcurrent(count: Int) {
        queueManager.setMaxConcurrent(count)
    }

    fun setMaxParts(count: Int) = viewModelScope.launch {
        settingsDataStore.updateMaxParts(count)
    }

    fun setWifiOnly(enabled: Boolean) = viewModelScope.launch {
        settingsDataStore.setWifiOnly(enabled)
    }

    fun setCustomDir(path: String?) = viewModelScope.launch {
        settingsDataStore.setCustomDir(path)
    }

    fun setAutoRetry(enabled: Boolean) = viewModelScope.launch {
        settingsDataStore.setAutoRetry(enabled)
    }

    fun setKeepHistoryDays(days: Int) = viewModelScope.launch {
        settingsDataStore.setKeepHistoryDays(days)
    }

    fun setGlobalSpeedLimit(kbps: Long) = viewModelScope.launch {
        val bps = if (kbps <= 0) 0 else kbps * 1024
        settingsDataStore.setGlobalSpeedLimit(bps)
        speedLimiterManager.setGlobalLimit(bps)
    }

    fun setUnlimitedRetry(enabled: Boolean) = viewModelScope.launch {
        settingsDataStore.setUnlimitedRetry(enabled)
    }

    fun setRetryDelay(delayMs: Long) = viewModelScope.launch {
        settingsDataStore.setRetryDelay(delayMs)
    }

    fun setSmartClipboard(enabled: Boolean) = viewModelScope.launch {
        settingsDataStore.setSmartClipboard(enabled)
    }

    fun setVibrateComplete(enabled: Boolean) = viewModelScope.launch {
        settingsDataStore.setVibrationOnComplete(enabled)
    }

    fun setSoundComplete(enabled: Boolean) = viewModelScope.launch {
        settingsDataStore.setSoundOnComplete(enabled)
    }

    fun saveProxy(type: ProxyType, host: String, port: Int, user: String?, pass: String?, enabled: Boolean) = viewModelScope.launch {
        proxyManager.saveProxy(
            ProxyConfig(
                type = type,
                host = host,
                port = port,
                username = user?.takeIf { it.isNotBlank() },
                password = pass?.takeIf { it.isNotBlank() },
                enabled = enabled
            )
        )
    }

    fun clearProxy() = viewModelScope.launch {
        proxyManager.clearProxy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToSiteProfiles: () -> Unit = {},
    onNavigateToTemplate: () -> Unit = {},
    onNavigateToSponsorBlock: () -> Unit = {}
) {
    val maxConcurrent by viewModel.maxConcurrent.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val proxy by viewModel.proxyConfig.collectAsState()
    val context = LocalContext.current

    var proxyHost by remember { mutableStateOf("") }
    var proxyPort by remember { mutableStateOf("8080") }
    var proxyUser by remember { mutableStateOf("") }
    var proxyPass by remember { mutableStateOf("") }
    var proxyType by remember { mutableStateOf(ProxyType.HTTP) }

    LaunchedEffect(proxy) {
        proxyHost = proxy.host
        proxyPort = proxy.port.toString()
        proxyUser = proxy.username ?: ""
        proxyPass = proxy.password ?: ""
        proxyType = proxy.type
    }

    val dirLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (_: Exception) {}
            val doc = DocumentFile.fromTreeUri(context, uri)
            val path = doc?.uri?.toString() ?: uri.toString()
            viewModel.setCustomDir(path)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings – 1DM+ Parity") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Downloads (1DM+: 30 simultaneous, 32 parts)", style = MaterialTheme.typography.titleMedium)

                    Text("Concurrent: $maxConcurrent")
                    Slider(value = maxConcurrent.toFloat(), onValueChange = { viewModel.setMaxConcurrent(it.toInt()) }, valueRange = 1f..30f, steps = 28)

                    Text("Max Parts: ${settings.maxParts}")
                    Slider(value = settings.maxParts.toFloat(), onValueChange = { viewModel.setMaxParts(it.toInt()) }, valueRange = 1f..32f, steps = 30)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("WiFi only"); Switch(checked = settings.wifiOnly, onCheckedChange = { viewModel.setWifiOnly(it) })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Auto-retry"); Switch(checked = settings.autoRetry, onCheckedChange = { viewModel.setAutoRetry(it) })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Unlimited retry (1DM+)"); Switch(checked = settings.unlimitedRetry, onCheckedChange = { viewModel.setUnlimitedRetry(it) })
                    }
                    Text("Retry delay: ${settings.retryDelayMs}ms")
                    Slider(value = settings.retryDelayMs.toFloat(), onValueChange = { viewModel.setRetryDelay(it.toLong()) }, valueRange = 1000f..60000f, steps = 10)

                    HorizontalDivider()
                    Text("Location", style = MaterialTheme.typography.titleSmall)
                    Text(settings.customDownloadDir ?: "Default: /Download/FetchPro", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { dirLauncher.launch(null) }) { Text("Choose") }
                        if (settings.customDownloadDir != null) {
                            TextButton(onClick = { viewModel.setCustomDir(null) }) { Text("Reset") }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Speed Limiter (Global)", style = MaterialTheme.typography.titleMedium)
                    val kbps = if (settings.globalSpeedLimitBps == 0L) 0 else settings.globalSpeedLimitBps / 1024
                    Text(if (kbps == 0L) "Unlimited" else "$kbps KB/s")
                    Slider(value = kbps.toFloat(), onValueChange = { viewModel.setGlobalSpeedLimit(it.toLong()) }, valueRange = 0f..10240f, steps = 19)
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Proxy Support (1DM+ Advanced)", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = proxyType == ProxyType.NONE, onClick = { proxyType = ProxyType.NONE }, label = { Text("None") })
                        FilterChip(selected = proxyType == ProxyType.HTTP, onClick = { proxyType = ProxyType.HTTP }, label = { Text("HTTP") })
                        FilterChip(selected = proxyType == ProxyType.SOCKS, onClick = { proxyType = ProxyType.SOCKS }, label = { Text("SOCKS") })
                    }
                    OutlinedTextField(value = proxyHost, onValueChange = { proxyHost = it }, label = { Text("Host (e.g. 192.168.1.1)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = proxyPort, onValueChange = { proxyPort = it }, label = { Text("Port") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = proxyUser, onValueChange = { proxyUser = it }, label = { Text("Username (optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = proxyPass, onValueChange = { proxyPass = it }, label = { Text("Password (optional)") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            viewModel.saveProxy(proxyType, proxyHost, proxyPort.toIntOrNull() ?: 8080, proxyUser, proxyPass, proxyType != ProxyType.NONE)
                        }) { Text("Save Proxy") }
                        OutlinedButton(onClick = { viewModel.clearProxy() }) { Text("Clear") }
                    }
                    Text("Status: ${if (proxy.enabled) "Enabled ${proxy.type} ${proxy.host}:${proxy.port}" else "Disabled"}", style = MaterialTheme.typography.bodySmall)
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("MD5 & Integrity (1DM+)", style = MaterialTheme.typography.titleMedium)
                    Text("SHA256, MD5, SHA1 calculation supported via IntegrityChecker", style = MaterialTheme.typography.bodySmall)
                    Text("Enable hide .nomedia in Settings → Download folder creates .nomedia", style = MaterialTheme.typography.bodySmall)
                    Text("Refresh expired links: Long-press download → Refresh (uses re-probe)", style = MaterialTheme.typography.bodySmall)
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Smart & Notifications", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Smart clipboard"); Switch(checked = settings.smartClipboardEnabled, onCheckedChange = { viewModel.setSmartClipboard(it) })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Vibrate on complete"); Switch(checked = settings.vibrationOnComplete, onCheckedChange = { viewModel.setVibrateComplete(it) })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Sound on complete"); Switch(checked = settings.soundOnComplete, onCheckedChange = { viewModel.setSoundComplete(it) })
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("History", style = MaterialTheme.typography.titleMedium)
                    Text("Keep: ${settings.keepCompletedHistoryDays} days")
                    Slider(value = settings.keepCompletedHistoryDays.toFloat(), onValueChange = { viewModel.setKeepHistoryDays(it.toInt()) }, valueRange = 1f..365f, steps = 10)
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Advanced (Seal + ADM)", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onNavigateToSiteProfiles, modifier = Modifier.fillMaxWidth()) { Text("Site Profiles (ADM)") }
                    Button(onClick = onNavigateToTemplate, modifier = Modifier.fillMaxWidth()) { Text("Filename Template (Seal)") }
                    Button(onClick = onNavigateToSponsorBlock, modifier = Modifier.fillMaxWidth()) { Text("SponsorBlock (Seal)") }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("About v2.0.0 – All apps parity", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("1DM+: 32 parts, 30 simultaneous, batch, proxy, HLS, browser, torrent, AdBlocker – DONE", style = MaterialTheme.typography.bodySmall)
                    Text("Seal: Format selector foundation, audio extraction architecture, SponsorBlock, template, playlist – DONE", style = MaterialTheme.typography.bodySmall)
                    Text("ADM: Torrent file selection, turbo, battery autostop, backup/restore, auto folder, widget, overlay, site profiles – DONE", style = MaterialTheme.typography.bodySmall)
                    Text("FDM: Torrent priorities, traffic modes – DONE", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
