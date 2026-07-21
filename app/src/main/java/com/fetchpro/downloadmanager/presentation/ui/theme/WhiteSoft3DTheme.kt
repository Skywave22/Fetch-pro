package com.fetchpro.downloadmanager.presentation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WhiteSoftLightColorScheme = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF64748B),
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF64748B),
    error = Color(0xFFEF4444),
    outline = Color(0xFFE2E8F0)
)

@Composable
fun WhiteSoft3DTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    // Force light theme for this 3D white design as per selected image
    MaterialTheme(
        colorScheme = WhiteSoftLightColorScheme,
        typography = Typography,
        content = content
    )
}
