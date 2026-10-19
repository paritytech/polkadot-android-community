package io.paritytech.polkadotapp.design.components.docklet

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.paritytech.polkadotapp.designsystem.colors.LocalPolkadotColors
import io.paritytech.polkadotapp.designsystem.themes.PolkadotAppTheme

object DockletColors {
    val GlassLight = Color(0xFF919191)
    val GlassDark = Color(0xFF2B2B2B)
    const val GlassFillAlpha = 0.2f
    val CardFill = Color(0xFF080808).copy(alpha = 0.3f)
    val Placeholder = Color(0xFF999999)
    val SectionHeader = Color(0xFF8E8E96)
    val Caret = Color(0xFF0A84FF)
    val ClearBackground = Color(0xFF3A3A3C)
    val ClearGlyph = Color(0xFFE5E5EA)
    val CameraBacking = Color(0xFF1A1B1F)
    val SearchIcon = Color(0xFFECECEC)
    val Shade = Color.Black
    val Ripple = Color.White
    val Light = Color.White
    val FieldFill = Color.White.copy(alpha = 0.06f)
    val Handle = Color.White.copy(alpha = 0.32f)
}

private const val TextSelectionAlpha = 0.34f

private const val LightPageLuminance = 0.5f

// The brief's glass values assume a dark page; on a light theme this base keeps the dock and the card dark.
private const val LightPageGlassBaseAlpha = 0.85f

internal val LocalDockletGlassBase = staticCompositionLocalOf { Color.Transparent }

@Composable
fun DockletGlassTheme(content: @Composable () -> Unit) {
    val colors = remember { PolkadotAppTheme.BerlinNight.colors() }
    val pageIsLight = LocalPolkadotColors.current.bg.surface.main.luminance() > LightPageLuminance
    val glassBase = if (pageIsLight) colors.bg.surface.main.copy(alpha = LightPageGlassBaseAlpha) else Color.Transparent
    CompositionLocalProvider(
        LocalDockletGlassBase provides glassBase,
        LocalPolkadotColors provides colors,
        LocalContentColor provides colors.fg.primary,
        LocalIndication provides ripple(color = colors.fg.primary),
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = colors.fg.tertiary,
            backgroundColor = colors.fg.primary.copy(alpha = TextSelectionAlpha),
        ),
        content = content,
    )
}
