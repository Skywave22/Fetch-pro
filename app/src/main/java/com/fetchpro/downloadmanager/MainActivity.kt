package com.fetchpro.downloadmanager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.fetchpro.downloadmanager.presentation.navigation.AppBottomNav
import com.fetchpro.downloadmanager.presentation.navigation.AppNavGraph
import com.fetchpro.downloadmanager.presentation.ui.theme.FetchProTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var sharedUrl: String? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            FetchProTheme {
                val navController = rememberNavController()
                Scaffold(
                    bottomBar = { AppBottomNav(navController = navController) }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        AppNavGraph(navController = navController, initialUrl = sharedUrl)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        sharedUrl = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") intent.getStringExtra(Intent.EXTRA_TEXT)
                else null
            }
            else -> null
        }?.let { extractUrl(it) }
    }

    private fun extractUrl(text: String): String? {
        // Extract first http(s) url from shared text
        val regex = Regex("(https?://\\S+)")
        return regex.find(text)?.value ?: if (text.startsWith("http")) text else null
    }
}
