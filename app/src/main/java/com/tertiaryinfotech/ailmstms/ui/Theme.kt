package com.tertiaryinfotech.ailmstms.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.tertiaryinfotech.ailmstms.data.Brand
import com.tertiaryinfotech.ailmstms.data.BrandPalette
import com.tertiaryinfotech.ailmstms.data.ThemeMode

/** Colours that Material's scheme has no slot for: the header gradient and status tones. */
data class LmsColors(
    val dark: Boolean,
    val headerStart: Color,
    val headerEnd: Color,
    val onHeader: Color,
    val onHeaderMuted: Color,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    /** Cards sit on the page as a lighter surface with a hairline border. */
    val card: Color,
    val cardBorder: Color,
    /** Inset fields and unselected segments. */
    val field: Color,
    /** Brand blue for filled surfaces that carry white content (the total card, the role switcher). */
    val brandFill: Color,
) {
    val headerBrush: Brush get() = Brush.linearGradient(listOf(headerStart, headerEnd))
}

val LocalLmsColors = staticCompositionLocalOf {
    LmsColors(false, Color(0xFF051C47), Color(0xFF0A3D8F), Color.White, Color(0xFFC9DAFF), Color(0xFF1B7F4B), Color(0xFFD5F2E1), Color(0xFF0B3D23), Color(0xFF9A6700), Color.White, Color(0xFFDCE2EE), Color(0xFFE9EEF7), Color(0xFF0A3D8F))
}

private fun rgb(value: Int) = Color(0xFF000000L or value.toLong())

fun resolveDark(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * The TIA TMS theme: brand blue with a navy header in light mode, a lighter blue on deep navy
 * in dark mode. [mode] comes from the user's profile; [brandHex] is the academy's colour and is
 * only used when [Brand.palette] finds it safe for text contrast.
 */
@Composable
fun LmsTheme(mode: ThemeMode, brandHex: String?, content: @Composable () -> Unit) {
    val dark = resolveDark(mode, isSystemInDarkTheme())
    val brand: BrandPalette = Brand.palette(brandHex)
    val primary = rgb(brand.primary)
    val deep = rgb(brand.deep)
    val soft = rgb(brand.onDark)

    val scheme = if (dark) {
        darkColorScheme(
            primary = soft, onPrimary = deep, primaryContainer = primary, onPrimaryContainer = Color(0xFFDCE8FF),
            secondary = Color(0xFFB4C5E8), onSecondary = Color(0xFF1B2D4F), secondaryContainer = Color(0xFF2A3F66), onSecondaryContainer = Color(0xFFD9E4FF),
            tertiary = Color(0xFFF2C56B), onTertiary = Color(0xFF3F2E00), tertiaryContainer = Color(0xFF5A4300), onTertiaryContainer = Color(0xFFFFDEA0),
            background = Color(0xFF0A1020), onBackground = Color(0xFFE3E8F4),
            surface = Color(0xFF0A1020), onSurface = Color(0xFFE3E8F4),
            surfaceVariant = Color(0xFF2A3757), onSurfaceVariant = Color(0xFFBCC6DC),
            surfaceContainerLowest = Color(0xFF060B17), surfaceContainerLow = Color(0xFF111A2E), surfaceContainer = Color(0xFF152038),
            surfaceContainerHigh = Color(0xFF1B2845), surfaceContainerHighest = Color(0xFF223153),
            outline = Color(0xFF8793AD), outlineVariant = Color(0xFF36425F),
            error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        )
    } else {
        lightColorScheme(
            primary = primary, onPrimary = Color.White, primaryContainer = Color(0xFFD9E5FF), onPrimaryContainer = deep,
            secondary = Color(0xFF475E8C), onSecondary = Color.White, secondaryContainer = Color(0xFFDCE5F9), onSecondaryContainer = Color(0xFF12274B),
            tertiary = Color(0xFF7A5900), onTertiary = Color.White, tertiaryContainer = Color(0xFFFFE8B5), onTertiaryContainer = Color(0xFF3A2A00),
            background = Color(0xFFF4F7FC), onBackground = Color(0xFF111A2B),
            surface = Color(0xFFFAFBFF), onSurface = Color(0xFF111A2B),
            surfaceVariant = Color(0xFFE1E7F5), onSurfaceVariant = Color(0xFF434E66),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF1F4FB), surfaceContainer = Color(0xFFEBEFF8),
            surfaceContainerHigh = Color(0xFFE5EAF4), surfaceContainerHighest = Color(0xFFDFE5F1),
            outline = Color(0xFF6F7A93), outlineVariant = Color(0xFFC3CBDD),
            error = Color(0xFFB3261E), onError = Color.White, errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
        )
    }
    val extras = if (dark) {
        LmsColors(true, Color(0xFF060B17), deep, Color.White, Color(0xFFB9CBF5), Color(0xFF7FD9A6), Color(0xFF0F4A2C), Color(0xFFC9F2DA), Color(0xFFF2C56B), Color(0xFF131C31), Color(0xFF2A3654), Color(0xFF1B2845), primary)
    } else {
        LmsColors(false, deep, primary, Color.White, Color(0xFFC9DAFF), Color(0xFF1B7F4B), Color(0xFFD5F2E1), Color(0xFF0B3D23), Color(0xFF8A5A00), Color.White, Color(0xFFDCE2EE), Color(0xFFE9EEF7), primary)
    }
    CompositionLocalProvider(LocalLmsColors provides extras) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
