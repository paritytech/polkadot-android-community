package io.paritytech.polkadotapp.design.components.docklet

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.paritytech.polkadotapp.designsystem.colors.LocalPolkadotColors
import io.paritytech.polkadotapp.designsystem.colors.PolkadotColorsPalette
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
private const val LightGlassBaseAlpha = 0.72f
private const val LightCardFillAlpha = 0.3f
private const val LightFieldFillAlpha = 0.06f
private const val LightHandleAlpha = 0.24f

@Immutable
data class DockletPalette(
    val glassBase: Color,
    val cardFill: Color,
    val placeholder: Color,
    val sectionHeader: Color,
    val searchIcon: Color,
    val fieldFill: Color,
    val handle: Color,
    val ripple: Color,
    val highlight: Color,
    val clearBackground: Color,
    val clearGlyph: Color,
)

private val DarkDockletPalette = DockletPalette(
    glassBase = Color.Transparent,
    cardFill = DockletColors.CardFill,
    placeholder = DockletColors.Placeholder,
    sectionHeader = DockletColors.SectionHeader,
    searchIcon = DockletColors.SearchIcon,
    fieldFill = DockletColors.FieldFill,
    handle = DockletColors.Handle,
    ripple = DockletColors.Ripple,
    highlight = DockletColors.Light,
    clearBackground = DockletColors.ClearBackground,
    clearGlyph = DockletColors.ClearGlyph,
)

private fun lightDockletPalette(colors: PolkadotColorsPalette) = DockletPalette(
    glassBase = colors.bg.surface.main.copy(alpha = LightGlassBaseAlpha),
    cardFill = colors.bg.surface.main.copy(alpha = LightCardFillAlpha),
    placeholder = colors.fg.secondary,
    sectionHeader = colors.fg.secondary,
    searchIcon = colors.fg.secondary,
    fieldFill = colors.fg.primary.copy(alpha = LightFieldFillAlpha),
    handle = colors.fg.primary.copy(alpha = LightHandleAlpha),
    ripple = colors.fg.primary,
    highlight = colors.fg.primary,
    clearBackground = colors.fg.tertiary,
    clearGlyph = colors.fg.primaryInverted,
)

val LocalDockletPalette = staticCompositionLocalOf { DarkDockletPalette }

@Composable
fun DockletGlassTheme(content: @Composable () -> Unit) {
    val pageColors = LocalPolkadotColors.current
    val pageIsLight = pageColors.bg.surface.main.luminance() > LightPageLuminance
    val darkColors = remember { PolkadotAppTheme.BerlinNight.colors() }
    val colors = if (pageIsLight) pageColors else darkColors
    val palette = remember(colors, pageIsLight) { if (pageIsLight) lightDockletPalette(colors) else DarkDockletPalette }
    CompositionLocalProvider(
        LocalDockletPalette provides palette,
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
