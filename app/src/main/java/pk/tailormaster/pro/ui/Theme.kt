package pk.tailormaster.pro.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import pk.tailormaster.pro.R

private val Teal = Color(0xFF0F4C5C)
private val TealLight = Color(0xFF5FA8B8)
private val Gold = Color(0xFFE3A72F)

private val Light = lightColorScheme(
    primary = Teal, onPrimary = Color.White,
    primaryContainer = Color(0xFFCBE6EC), onPrimaryContainer = Color(0xFF00202A),
    secondary = Color(0xFF8A5A00), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE2AE), onSecondaryContainer = Color(0xFF2B1A00),
    tertiary = Color(0xFF4F6354),
    background = Color(0xFFF7F8F6), onBackground = Color(0xFF191C1C),
    surface = Color.White, onSurface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFFE6ECEC), onSurfaceVariant = Color(0xFF3F4949),
    error = Color(0xFFB3261E), errorContainer = Color(0xFFF9DEDC),
)

private val Dark = darkColorScheme(
    primary = TealLight, onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF0F4C5C), onPrimaryContainer = Color(0xFFCBE6EC),
    secondary = Gold, onSecondary = Color(0xFF3F2E00),
    secondaryContainer = Color(0xFF5B4300), onSecondaryContainer = Color(0xFFFFE2AE),
    background = Color(0xFF111414), onBackground = Color(0xFFE0E3E2),
    surface = Color(0xFF1A1D1D), onSurface = Color(0xFFE0E3E2),
    surfaceVariant = Color(0xFF2A3131), onSurfaceVariant = Color(0xFFBEC8C8),
)

object Semantic {
    val ok = Color(0xFF1E7D32)
    val warn = Color(0xFFB26A00)
    val bad = Color(0xFFC62828)
}

fun fontFamilyFor(key: String): FontFamily = when (key) {
    "naskh" -> FontFamily(Font(R.font.naskh, FontWeight.Normal), Font(R.font.naskh, FontWeight.Bold))
    "nastaliq" -> FontFamily(Font(R.font.nastaliq, FontWeight.Normal), Font(R.font.nastaliq, FontWeight.Bold))
    else -> FontFamily.Default
}

@Composable
fun TailorTheme(themeKey: String, fontKey: String, content: @Composable () -> Unit) {
    val dark = when (themeKey) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val ff = fontFamilyFor(fontKey)
    val lh = if (fontKey == "nastaliq") 1.9f else 1.35f
    fun st(size: Int, w: FontWeight = FontWeight.Normal) = TextStyle(fontFamily = ff, fontSize = size.sp, fontWeight = w, lineHeight = (size * lh).sp)
    val typo = Typography(
        displaySmall = st(30, FontWeight.Bold),
        headlineSmall = st(23, FontWeight.Bold),
        titleLarge = st(21, FontWeight.Bold),
        titleMedium = st(18, FontWeight.Bold),
        titleSmall = st(16, FontWeight.Bold),
        bodyLarge = st(17),
        bodyMedium = st(15),
        bodySmall = st(13),
        labelLarge = st(16, FontWeight.Bold),
        labelMedium = st(14),
        labelSmall = st(12),
    )
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = typo) {
        CompositionLocalProvider(LocalLayoutDirection provides if (pk.tailormaster.pro.i18n.I18n.en) LayoutDirection.Ltr else LayoutDirection.Rtl) {
            content()
        }
    }
}
