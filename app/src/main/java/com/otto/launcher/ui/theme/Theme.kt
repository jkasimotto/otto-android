package com.otto.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = OttoWhite,
    onPrimary = OttoBlack,
    background = OttoBlack,
    onBackground = OttoWhite,
    surface = OttoInk,
    onSurface = OttoWhite,
    surfaceVariant = OttoGraphite,
    onSurfaceVariant = OttoSilver
)

@Composable
fun OttoLauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography,
        content = content
    )
}
