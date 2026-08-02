package com.djand.hst.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.djand.hst.data.settings.ThemeMode

/**
 * Minimal, low-clutter Material 3 theme (StrongLifts philosophy: a single accent
 * colour, everything else neutral). Typography is left at the Material defaults,
 * which are intentionally large and readable.
 */
private val Accent = Color(0xFF1565C0)

private val LightColors = lightColorScheme(
    primary = Accent,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
)

@Composable
fun HstTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
