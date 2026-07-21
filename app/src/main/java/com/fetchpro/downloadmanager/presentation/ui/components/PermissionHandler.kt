package com.fetchpro.downloadmanager.presentation.ui.components

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NotificationPermissionHandler(
    content: @Composable () -> Unit
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        content()
        return
    }

    val permissionState = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    var asked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!permissionState.status.isGranted && !asked) {
            permissionState.launchPermissionRequest()
            asked = true
        }
    }

    if (permissionState.status.isGranted) {
        content()
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Notifications Permission Required", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("FetchPro needs notification permission to show download progress in the background.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { permissionState.launchPermissionRequest() }) {
                        Text("Grant Permission")
                    }
                    if (permissionState.status is PermissionStatus.Denied && (permissionState.status as PermissionStatus.Denied).shouldShowRationale) {
                        Spacer(Modifier.height(8.dp))
                        Text("Permission is important for background downloads.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { /* allow skip */ }, modifier = Modifier.fillMaxWidth()) {
                Text("Continue without notifications (not recommended)")
            }
            Spacer(Modifier.height(8.dp))
            // Still show content but without notifications
            content()
        }
    }
}
