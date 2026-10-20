package com.miking.notas.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.miking.notas.data.BackgroundStyle
import com.miking.notas.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F5BD5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFE0FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF5C5D72),
    secondaryContainer = Color(0xFFE1E0F9),
    background = Color(0xFFFBF8FF),
    surface = Color(0xFFFBF8FF),
    surfaceVariant = Color(0xFFE4E1EC),
    onSurface = Color(0xFF1B1B21),
    onSurfaceVariant = Color(0xFF46464F),
    outlineVariant = Color(0xFFC7C5D0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBEC2FF),
    onPrimary = Color(0xFF1B2578),
    primaryContainer = Color(0xFF363F9B),
    onPrimaryContainer = Color(0xFFDFE0FF),
    secondary = Color(0xFFC5C4DD),
    secondaryContainer = Color(0xFF444559),
    background = Color(0xFF121218),
    surface = Color(0xFF121218),
    surfaceVariant = Color(0xFF46464F),
    onSurface = Color(0xFFE5E1E9),
    onSurfaceVariant = Color(0xFFC7C5D0),
    outlineVariant = Color(0xFF46464F),
)

private val NotasTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        ),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val LightNoteColors = listOf(
    Color(0xFFFFF3C4), // amarillo suave
    Color(0xFFDDF4E3), // verde menta
    Color(0xFFDDE8FF), // azul cielo
    Color(0xFFFFE0E9), // rosa
    Color(0xFFEADFFF), // lavanda
)

private val DarkNoteColors = listOf(
    Color(0xFF4A3F1A),
    Color(0xFF1E3A29),
    Color(0xFF1D2F4A),
    Color(0xFF4A1F2B),
    Color(0xFF33234A),
)

/** Color de fondo de una nota según su índice de paleta y el tema activo. */
@Composable
fun noteContainerColor(index: Int): Color {
    val scheme = MaterialTheme.colorScheme
    if (index <= 0) return scheme.surfaceContainerLow
    val dark = scheme.background.luminance() < 0.5f
    val palette = if (dark) DarkNoteColors else LightNoteColors
    return palette[(index - 1).coerceIn(palette.indices)]
}

private data class BackgroundPalette(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val lowest: Color,
    val low: Color,
    val container: Color,
    val high: Color,
    val highest: Color,
    val outline: Color,
)

private val LightBackgroundPalettes = mapOf(
    BackgroundStyle.PAPER to BackgroundPalette(
        background = Color(0xFFFAF6EE),
        surface = Color(0xFFFDFBF6),
        surfaceVariant = Color(0xFFEDE5D8),
        lowest = Color(0xFFFFFDF9),
        low = Color(0xFFF5EFE4),
        container = Color(0xFFEFE7D9),
        high = Color(0xFFE9DFCE),
        highest = Color(0xFFE2D6C1),
        outline = Color(0xFFD0C5B4),
    ),
    BackgroundStyle.SAND to BackgroundPalette(
        background = Color(0xFFFBF1E3),
        surface = Color(0xFFFFF8F0),
        surfaceVariant = Color(0xFFF0E0CD),
        lowest = Color(0xFFFFFBF6),
        low = Color(0xFFF7EBDD),
        container = Color(0xFFF2E2CD),
        high = Color(0xFFECD9C0),
        highest = Color(0xFFE5CFB2),
        outline = Color(0xFFD5C1A8),
    ),
    BackgroundStyle.SAGE to BackgroundPalette(
        background = Color(0xFFF1F7F0),
        surface = Color(0xFFF8FCF7),
        surfaceVariant = Color(0xFFDDE8DC),
        lowest = Color(0xFFFCFEFB),
        low = Color(0xFFEEF5ED),
        container = Color(0xFFE7F0E5),
        high = Color(0xFFDFEADB),
        highest = Color(0xFFD7E3D2),
        outline = Color(0xFFC3D1C0),
    ),
    BackgroundStyle.SKY to BackgroundPalette(
        background = Color(0xFFEEF6FB),
        surface = Color(0xFFF8FBFE),
        surfaceVariant = Color(0xFFDCE8F0),
        lowest = Color(0xFFFCFDFF),
        low = Color(0xFFF0F7FC),
        container = Color(0xFFE6F1F8),
        high = Color(0xFFDDEAF3),
        highest = Color(0xFFD3E2ED),
        outline = Color(0xFFBFD0DD),
    ),
    BackgroundStyle.LAVENDER to BackgroundPalette(
        background = Color(0xFFF5F1FB),
        surface = Color(0xFFFBF8FE),
        surfaceVariant = Color(0xFFE7E0F0),
        lowest = Color(0xFFFEFCFF),
        low = Color(0xFFF5F0FB),
        container = Color(0xFFEDE6F5),
        high = Color(0xFFE5DCF0),
        highest = Color(0xFFDCD1E9),
        outline = Color(0xFFCBC0D8),
    ),
    BackgroundStyle.ROSE to BackgroundPalette(
        background = Color(0xFFFCF0F3),
        surface = Color(0xFFFFF9FA),
        surfaceVariant = Color(0xFFF1DFE4),
        lowest = Color(0xFFFFFCFD),
        low = Color(0xFFF9EEF1),
        container = Color(0xFFF4E4E8),
        high = Color(0xFFEEDAE0),
        highest = Color(0xFFE6CFD6),
        outline = Color(0xFFD6BEC5),
    ),
)

private val DarkBackgroundPalettes = mapOf(
    BackgroundStyle.PAPER to BackgroundPalette(
        background = Color(0xFF1A1815),
        surface = Color(0xFF201D19),
        surfaceVariant = Color(0xFF4B453B),
        lowest = Color(0xFF151310),
        low = Color(0xFF23201B),
        container = Color(0xFF2A2620),
        high = Color(0xFF332E27),
        highest = Color(0xFF3C362D),
        outline = Color(0xFF514A3F),
    ),
    BackgroundStyle.SAND to BackgroundPalette(
        background = Color(0xFF1D1914),
        surface = Color(0xFF241F19),
        surfaceVariant = Color(0xFF51483B),
        lowest = Color(0xFF17130F),
        low = Color(0xFF27221B),
        container = Color(0xFF2F2921),
        high = Color(0xFF383128),
        highest = Color(0xFF423A2F),
        outline = Color(0xFF574D40),
    ),
    BackgroundStyle.SAGE to BackgroundPalette(
        background = Color(0xFF131A14),
        surface = Color(0xFF19211A),
        surfaceVariant = Color(0xFF3F4A3F),
        lowest = Color(0xFF0F1510),
        low = Color(0xFF1B241C),
        container = Color(0xFF222C23),
        high = Color(0xFF2A352B),
        highest = Color(0xFF323E33),
        outline = Color(0xFF465347),
    ),
    BackgroundStyle.SKY to BackgroundPalette(
        background = Color(0xFF111820),
        surface = Color(0xFF172029),
        surfaceVariant = Color(0xFF3A4653),
        lowest = Color(0xFF0D1319),
        low = Color(0xFF19232D),
        container = Color(0xFF202B36),
        high = Color(0xFF283440),
        highest = Color(0xFF303D4A),
        outline = Color(0xFF44525F),
    ),
    BackgroundStyle.LAVENDER to BackgroundPalette(
        background = Color(0xFF18131F),
        surface = Color(0xFF1F1927),
        surfaceVariant = Color(0xFF483F54),
        lowest = Color(0xFF130F19),
        low = Color(0xFF211B29),
        container = Color(0xFF292232),
        high = Color(0xFF322A3C),
        highest = Color(0xFF3B3246),
        outline = Color(0xFF50465D),
    ),
    BackgroundStyle.ROSE to BackgroundPalette(
        background = Color(0xFF1F1418),
        surface = Color(0xFF281A1F),
        surfaceVariant = Color(0xFF543F46),
        lowest = Color(0xFF190F13),
        low = Color(0xFF2A1C21),
        container = Color(0xFF332329),
        high = Color(0xFF3D2B32),
        highest = Color(0xFF47333B),
        outline = Color(0xFF5C454D),
    ),
)

private fun BackgroundStyle.palette(dark: Boolean): BackgroundPalette? =
    (if (dark) DarkBackgroundPalettes else LightBackgroundPalettes)[this]

private fun ColorScheme.withBackground(
    style: BackgroundStyle,
    dark: Boolean,
): ColorScheme {
    val palette = style.palette(dark) ?: return this
    return copy(
        background = palette.background,
        surface = palette.surface,
        surfaceVariant = palette.surfaceVariant,
        surfaceContainerLowest = palette.lowest,
        surfaceContainerLow = palette.low,
        surfaceContainer = palette.container,
        surfaceContainerHigh = palette.high,
        surfaceContainerHighest = palette.highest,
        outlineVariant = palette.outline,
    )
}

/** Color de muestra para el selector de fondos en Ajustes. */
@Composable
fun backgroundPreviewColor(
    style: BackgroundStyle,
    dynamicColor: Boolean = true,
): Color {
    val context = LocalContext.current
    val current = MaterialTheme.colorScheme
    val dark = current.background.luminance() < 0.5f

    if (style == BackgroundStyle.DEFAULT) {
        return when {
            dynamicColor -> {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            dark -> DarkColors
            else -> LightColors
        }.background
    }

    return style.palette(dark)?.background ?: current.background
}

@Composable
fun NotasTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    backgroundStyle: BackgroundStyle = BackgroundStyle.DEFAULT,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val baseScheme = when {
        dynamicColor -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val colorScheme = baseScheme.withBackground(backgroundStyle, darkTheme)
    val lightStatusBarIcons = colorScheme.background.luminance() >= 0.5f

    val view = LocalView.current
    if (!view.isInEditMode) {
        LaunchedEffect(lightStatusBarIcons) {
            val window = view.context.findActivity()?.window ?: return@LaunchedEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightStatusBarIcons
                isAppearanceLightNavigationBars = lightStatusBarIcons
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NotasTypography,
        content = content,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
