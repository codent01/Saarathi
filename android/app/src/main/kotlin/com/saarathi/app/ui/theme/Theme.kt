package com.saarathi.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.saarathi.app.data.prefs.ThemeKey

/** Extra colours the Material scheme does not carry: bubbles, gold accents, shloka card glow. */
data class SaarathiColors(
    val background: Color, val surface: Color, val surfaceElevated: Color, val accent: Color, val gold: Color,
    val textPrimary: Color, val textSecondary: Color, val userBubble: Color, val userBubbleText: Color,
    val krishnaBubble: Color, val krishnaBubbleBorder: Color, val shlokaCard: Color, val shlokaBorder: Color,
    val danger: Color, val success: Color,
)

val DivineEmber = SaarathiColors(
    background = Color(0xFF111115), surface = Color(0xFF1D1B20), surfaceElevated = Color(0xFF26232B),
    accent = Color(0xFFFF8828), gold = Color(0xFFCFA86B), textPrimary = Color(0xFFF5F0E8), textSecondary = Color(0xFFB6ADA4),
    userBubble = Color(0xFF7A3A12), userBubbleText = Color(0xFFFFF3E6), krishnaBubble = Color(0xFF232026),
    krishnaBubbleBorder = Color(0xFF3A332C), shlokaCard = Color(0xFF1A1714), shlokaBorder = Color(0xFF8A6A3A),
    danger = Color(0xFFE5655A), success = Color(0xFF7FBF8A),
)

val Kurukshetra = SaarathiColors(
    background = Color(0xFF0D1220), surface = Color(0xFF161D30), surfaceElevated = Color(0xFF1E273D),
    accent = Color(0xFFF5892E), gold = Color(0xFFC9955C), textPrimary = Color(0xFFF2EEE6), textSecondary = Color(0xFFABA8B4),
    userBubble = Color(0xFF6F3C14), userBubbleText = Color(0xFFFFF1E2), krishnaBubble = Color(0xFF1B2335),
    krishnaBubbleBorder = Color(0xFF2E3A52), shlokaCard = Color(0xFF131A2B), shlokaBorder = Color(0xFF8E6B3C),
    danger = Color(0xFFE5655A), success = Color(0xFF7FBF8A),
)

val EternalCalm = SaarathiColors(
    background = Color(0xFF16181A), surface = Color(0xFF212427), surfaceElevated = Color(0xFF2A2E32),
    accent = Color(0xFFD08A5A), gold = Color(0xFFC8AE8A), textPrimary = Color(0xFFF4EFE7), textSecondary = Color(0xFFB4ADA3),
    userBubble = Color(0xFF5E4330), userBubbleText = Color(0xFFFBF3EA), krishnaBubble = Color(0xFF26292D),
    krishnaBubbleBorder = Color(0xFF3B3F44), shlokaCard = Color(0xFF1E1C1A), shlokaBorder = Color(0xFF8C7A5F),
    danger = Color(0xFFE5655A), success = Color(0xFF7FBF8A),
)

val LocalSaarathiColors = staticCompositionLocalOf { DivineEmber }

object SaarathiTheme {
    val colors: SaarathiColors @Composable get() = LocalSaarathiColors.current
}

fun colorsFor(key: ThemeKey): SaarathiColors = when (key) {
    ThemeKey.DIVINE_EMBER -> DivineEmber
    ThemeKey.KURUKSHETRA -> Kurukshetra
    ThemeKey.ETERNAL_CALM -> EternalCalm
}

private fun scheme(c: SaarathiColors): ColorScheme = darkColorScheme(
    primary = c.accent, onPrimary = Color(0xFF1A0E04), secondary = c.gold, onSecondary = Color(0xFF1A1206),
    background = c.background, onBackground = c.textPrimary, surface = c.surface, onSurface = c.textPrimary,
    surfaceVariant = c.surfaceElevated, onSurfaceVariant = c.textSecondary, outline = c.krishnaBubbleBorder,
    error = c.danger, onError = Color.White, tertiary = c.gold,
)

fun typographyFor(scale: Float): Typography {
    fun s(size: Int, weight: FontWeight = FontWeight.Normal, lineHeight: Int = (size * 1.45).toInt()) =
        TextStyle(fontFamily = FontFamily.SansSerif, fontSize = (size * scale).sp, fontWeight = weight, lineHeight = (lineHeight * scale).sp)
    return Typography(
        displayLarge = s(34, FontWeight.SemiBold, 40), headlineMedium = s(24, FontWeight.SemiBold, 30), headlineSmall = s(20, FontWeight.SemiBold, 26),
        titleLarge = s(19, FontWeight.SemiBold, 24), titleMedium = s(16, FontWeight.SemiBold, 22), titleSmall = s(14, FontWeight.SemiBold, 20),
        bodyLarge = s(16, lineHeight = 24), bodyMedium = s(15, lineHeight = 22), bodySmall = s(13, lineHeight = 18),
        labelLarge = s(14, FontWeight.Medium, 20), labelMedium = s(12, FontWeight.Medium, 16), labelSmall = s(11, FontWeight.Medium, 14),
    )
}

@Composable
fun SaarathiTheme(themeKey: ThemeKey, textScale: Float, content: @Composable () -> Unit) {
    val colors = colorsFor(themeKey)
    // The app is always dark; the system setting is read only so previews behave.
    isSystemInDarkTheme()
    CompositionLocalProvider(LocalSaarathiColors provides colors) {
        MaterialTheme(colorScheme = scheme(colors), typography = typographyFor(textScale), content = content)
    }
}
