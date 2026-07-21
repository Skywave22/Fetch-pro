package com.fetchpro.downloadmanager.presentation.ui.screens.browser

import android.annotation.SuppressLint
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.fetchpro.downloadmanager.browser.DownloadInterceptor
import com.fetchpro.downloadmanager.browser.adblock.AdBlocker

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel = hiltViewModel(),
    onDownloadRequest: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTab = uiState.currentTab
    var urlInput by remember { mutableStateOf(currentTab?.url ?: "https://www.google.com") }
    var showBookmarks by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    LaunchedEffect(currentTab?.url) {
        urlInput = currentTab?.url ?: ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isIncognito) "Incognito" else "Browser") },
                navigationIcon = {
                    Row {
                        IconButton(onClick = { viewModel.newTab(incognito = false) }) {
                            Icon(Icons.Default.Add, contentDescription = "New Tab")
                        }
                        if (uiState.tabs.size > 1) {
                            Badge { Text("${uiState.tabs.size}") }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleBookmark() }) {
                        Icon(Icons.Default.Bookmark, contentDescription = "Bookmark")
                    }
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                    IconButton(onClick = { showBookmarks = true }) {
                        Icon(Icons.Default.Star, contentDescription = "Bookmarks")
                    }
                    IconButton(onClick = { viewModel.newTab(incognito = true) }) {
                        Icon(Icons.Default.VisibilityOff, contentDescription = "Incognito")
                    }
                }
            )
        },
        bottomBar = {
            Column {
                // Detected media links bar (1DM+ auto-catch)
                if (uiState.detectedLinks.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Detected ${uiState.detectedLinks.size} media files (1DM+ auto-catch)", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(4.dp))
                            LazyColumn(modifier = Modifier.heightIn(max = 120.dp)) {
                                items(uiState.detectedLinks) { link ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(link, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                                        TextButton(onClick = { viewModel.downloadLink(link) }) { Text("Download") }
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { viewModel.downloadAllDetected() }) { Text("Download All") }
                                OutlinedButton(onClick = { viewModel.clearDetected() }) { Text("Clear") }
                            }
                        }
                    }
                }

                // URL bar + tabs
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Enter URL") }
                    )
                    Button(onClick = {
                        var url = urlInput.trim()
                        if (!url.startsWith("http")) url = "https://$url"
                        viewModel.navigateCurrentTab(url)
                    }) {
                        Text("Go")
                    }
                }
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.tabs) { tab ->
                        FilterChip(
                            selected = tab.id == uiState.currentTabId,
                            onClick = { viewModel.selectTab(tab.id) },
                            label = { Text(tab.title?.take(15) ?: tab.url.take(20), maxLines = 1) },
                            trailingIcon = {
                                IconButton(onClick = { viewModel.closeTab(tab.id) }, modifier = Modifier.size(18.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(12.dp))
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (currentTab != null) {
                val context = LocalContext.current
                val adBlocker = remember { AdBlocker(context) }
                LaunchedEffect(Unit) {
                    adBlocker.initialize()
                }
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10) FetchPro/1.0"
                            if (currentTab.isIncognito) {
                                clearCache(true)
                                clearHistory()
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val url = request?.url.toString()
                                    // Check if downloadable
                                    if (DownloadInterceptor.isDownloadableUrl(url, null, null)) {
                                        viewModel.downloadLink(url)
                                        onDownloadRequest(url)
                                        return true
                                    }
                                    return false
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    url?.let { viewModel.onPageFinished(it, view?.title) }

                                    // Inject JS to detect media links (VIDEO/MUSIC)
                                    view?.evaluateJavascript(
                                        """
                                        (function() {
                                            var links = [];
                                            var videos = document.querySelectorAll('video source, audio source, a[href$=\".mp4\"], a[href$=\".mp3\"], a[href$=\".mkv\"], a[href$=\".webm\"]');
                                            for(var i=0; i<videos.length; i++) {
                                                var src = videos[i].src || videos[i].href;
                                                if(src) links.push(src);
                                            }
                                            var allLinks = document.querySelectorAll('a[href]');
                                            for(var j=0; j<allLinks.length; j++) {
                                                var h = allLinks[j].href;
                                                if(h.match(/\\.(mp4|mp3|mkv|webm|m4a|flac|wav|avi|mov)$/i)) links.push(h);
                                            }
                                            return JSON.stringify([...new Set(links)]);
                                        })()
                                        """.trimIndent()
                                    ) { result ->
                                        try {
                                            val clean = result.trim('"').replace("\\\"", "\"").replace("\\\\", "\\")
                                            if (clean.startsWith("[")) {
                                                val regex = Regex("\"(https?://[^\"]+)\"")
                                                val found = regex.findAll(clean).map { it.groupValues[1] }.toList()
                                                if (found.isNotEmpty()) {
                                                    viewModel.onLinksDetected(found)
                                                }
                                            }
                                        } catch (_: Exception) {}
                                    }
                                }

                                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                    val url = request?.url.toString()
                                    // AdBlocker - 1DM+ ad-free experience
                                    if (adBlocker.shouldBlock(url) || adBlocker.isAdUrl(url)) {
                                        return WebResourceResponse("text/plain", "utf-8", null) // block
                                    }
                                    // Detect HLS .m3u8
                                    if (url.contains(".m3u8")) {
                                        viewModel.onLinksDetected(listOf(url))
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }
                            }

                            webChromeClient = WebChromeClient()

                            setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                                // 1DM+ behavior: intercept downloads
                                viewModel.downloadLink(url)
                                onDownloadRequest(url)
                            }

                            loadUrl(currentTab.url)
                        }
                    },
                    update = { webView ->
                        if (webView.url != currentTab.url) {
                            webView.loadUrl(currentTab.url)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Bookmarks sheet
        if (showBookmarks) {
            ModalBottomSheet(onDismissRequest = { showBookmarks = false }) {
                Column(Modifier.padding(16.dp)) {
                    Text("Bookmarks", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn {
                        items(uiState.bookmarks) { bm ->
                            ListItem(
                                headlineContent = { Text(bm.title ?: bm.url) },
                                supportingContent = { Text(bm.url, maxLines = 1) },
                                modifier = Modifier.fillMaxWidth(),
                                trailingContent = {
                                    TextButton(onClick = { viewModel.navigateCurrentTab(bm.url); showBookmarks = false }) { Text("Open") }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showHistory) {
            ModalBottomSheet(onDismissRequest = { showHistory = false }) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("History", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { viewModel.clearHistory() }) { Text("Clear") }
                    }
                    LazyColumn {
                        items(uiState.history) { entry ->
                            ListItem(
                                headlineContent = { Text(entry.title ?: entry.url) },
                                supportingContent = { Text(entry.url, maxLines = 1) },
                                trailingContent = {
                                    TextButton(onClick = { viewModel.navigateCurrentTab(entry.url); showHistory = false }) { Text("Open") }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
