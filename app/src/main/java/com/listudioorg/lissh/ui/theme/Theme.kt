package com.listudioorg.lissh.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 固定深色（Termius 风格），与手机/平板一致体验。
 */
private val Colors = darkColorScheme(
    primary = Color(0xFF5A9CF8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF23303F),
    onPrimaryContainer = Color(0xFFCFE4FF),
    secondary = Color(0xFF93B2D6),
    onSecondary = Color(0xFF10151B),
    secondaryContainer = Color(0xFF26313D),
    onSecondaryContainer = Color(0xFFD3E3F6),
    tertiary = Color(0xFF6BCB8B),
    onTertiary = Color(0xFF0C1A10),
    background = Color(0xFF121419),
    onBackground = Color(0xFFE4E7EB),
    surface = Color(0xFF171A21),
    onSurface = Color(0xFFE4E7EB),
    surfaceVariant = Color(0xFF22262F),
    onSurfaceVariant = Color(0xFF9AA3AF),
    surfaceContainer = Color(0xFF1B1E26),
    surfaceContainerHigh = Color(0xFF22262F),
    outline = Color(0xFF3A4150),
    outlineVariant = Color(0xFF2B313C),
    error = Color(0xFFF0644E),
    onError = Color(0xFF250A05),
    errorContainer = Color(0xFF4A1F16),
    onErrorContainer = Color(0xFFFDD9D3),
)

@Composable
fun LiSSHTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
