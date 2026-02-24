package com.otto.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = OttoLilac,
    onPrimary = OttoCharcoal,
    background = OttoCharcoal,
    onBackground = OttoLilac,
    surface = OttoCharcoal,
    onSurface = OttoLilac,
    surfaceVariant = OttoGraphite,
    onSurfaceVariant = OttoLilac
)

@Composable
fun OttoLauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography,
        content = content
    )
}
