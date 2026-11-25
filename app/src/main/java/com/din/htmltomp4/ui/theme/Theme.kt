package com.din.htmltomp4.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ---------- Palette ----------
val PrimaryPurple = Color(0xFF6200EE)
val PrimaryPurpleVariant = Color(0xFF9A4DFF)
val SecondaryTeal = Color(0xFF03DAC6)
val BackgroundDark = Color(0xFF0E0E12)
val SurfaceDark = Color(0xFF1A1A22)
val SurfaceGlass = Color(0x33FFFFFF) // translucent white overlay for glass cards
val ErrorRed = Color(0xFFCF6679)
val OnDarkText = Color(0xFFEDEDF2)
val OnDarkTextMuted = Color(0xFFA6A6B3)

val AppGradient = Brush.linearGradient(
    colors = listOf(PrimaryPurple, PrimaryPurpleVariant, SecondaryTeal)
)

/** Soft glass border used on premium cards — a faint light sheen top-left fading to nothing. */
val GlassBorderBrush = Brush.linearGradient(
    colors = listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.02f))
)

/** Subtle vertical fill for glass surfaces — barely-there, just enough to lift off the background. */
val GlassFillBrush = Brush.verticalGradient(
    colors = listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.025f))
)

private val DarkColors = darkColorScheme(
    primary = PrimaryPurple,
    secondary = SecondaryTeal,
    background = BackgroundDark,
    surface = SurfaceDark,
    error = ErrorRed,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = OnDarkText,
    onSurface = OnDarkText
)

val AppTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, color = OnDarkText, letterSpacing = 0.2.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = OnDarkText),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, color = OnDarkTextMuted, lineHeight = 19.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, color = OnDarkText)
)

/**
 * Reusable glassmorphic surface modifier: soft translucent fill, a faint light-sheen border,
 * and rounded corners. This is the shared "premium" surface used across upload cards,
 * history rows, and result panels.
 */
fun Modifier.glassCard(cornerRadius: Int = 24): Modifier = this
    .clip(RoundedCornerShape(cornerRadius.dp))
    .background(GlassFillBrush)
    .border(width = 1.dp, brush = GlassBorderBrush, shape = RoundedCornerShape(cornerRadius.dp))

@Composable
fun HtmlToMp4Theme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        content = content
    )
}