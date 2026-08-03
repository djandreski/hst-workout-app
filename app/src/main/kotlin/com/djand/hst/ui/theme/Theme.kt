package com.djand.hst.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.djand.hst.data.settings.ThemeMode

/**
 * HST design system (DESIGN.md): StrongLifts-style minimalism — one brand red
 * (#D32F2F) for primary action / completion / focus, everything else neutral.
 * Typography is left at Material 3 defaults; emphasis comes from fontWeight.
 *
 * The M3 `error` role is reserved for real failures (failed save/import/export).
 * "Missed / needs a decision" states (missed reps, deload and pull-up banners)
 * use the custom amber [HstAttention.attention] token instead, so they never get
 * confused with the red "done" pips (DESIGN.md §3.2).
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFFD32F2F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    background = Color(0xFFF9F9F7),
    onBackground = Color(0xFF1B1B1B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B1B),
    surfaceVariant = Color(0xFFECECEC),
    onSurfaceVariant = Color(0xFF6E6E6E),
    outline = Color(0xFFDDDDDD),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF6E6E),
    onPrimary = Color(0xFF4A0002),
    background = Color(0xFF121212),
    onBackground = Color(0xFFECECEC),
    surface = Color(0xFF1C1C1C),
    onSurface = Color(0xFFECECEC),
    surfaceVariant = Color(0xFF2B2B2B),
    onSurfaceVariant = Color(0xFFB0B0B0),
    outline = Color(0xFF3A3A3A),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

/** Custom semantic tokens for "missed / notice" states (DESIGN.md §3.2). */
class AttentionColors(val attention: Color, val onAttention: Color)

private val LightAttention = AttentionColors(
    attention = Color(0xFFF9A825),
    onAttention = Color(0xFF3E2E00),
)

private val DarkAttention = AttentionColors(
    attention = Color(0xFFFFC947),
    onAttention = Color(0xFF1B1400),
)

private val LocalAttentionColors = staticCompositionLocalOf { LightAttention }

/** Access to the custom attention tokens: `HstAttention.attention` / `HstAttention.onAttention`. */
object HstAttention {
    val attention: Color
        @Composable get() = LocalAttentionColors.current.attention
    val onAttention: Color
        @Composable get() = LocalAttentionColors.current.onAttention
}

/** Near-black fill of the docked rest-timer bar — deliberately dark chrome even in light theme (DESIGN.md §10.3). */
val RestBarColor = Color(0xFF1C1C1C)

/**
 * Component shapes (DESIGN.md §5): cards 16dp (M3 Card uses `medium`), dialogs
 * 24dp (M3 AlertDialog uses `extraLarge`), rest-timer bar 20dp (`large`).
 */
private val HstShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Fully-rounded pill for the tall primary buttons (Start/Finish), 56–72dp high. */
val HstButtonShape = RoundedCornerShape(28.dp)

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
    CompositionLocalProvider(
        LocalAttentionColors provides if (darkTheme) DarkAttention else LightAttention,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            shapes = HstShapes,
            content = content,
        )
    }
}
