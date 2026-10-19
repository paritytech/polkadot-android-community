package io.paritytech.polkadotapp.design.components.docklet

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonColors
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonStyle
import io.paritytech.polkadotapp.design.components.button.icon.PolkadotIconButtonSize

internal val LobeTouchTarget = 48.dp

@Composable
fun rememberDockletFieldButtonStyle(): PolkadotButtonStyle {
    val palette = LocalDockletPalette.current
    return remember(palette) { DockletFieldButtonStyle(palette) }
}

@Immutable
private data class DockletFieldButtonStyle(private val palette: DockletPalette) : PolkadotButtonStyle {
    override val colors = PolkadotButtonColors(
        containerBrush = SolidColor(palette.fieldFill),
        contentColor = palette.searchIcon,
        disabledContainerBrush = SolidColor(palette.fieldFill),
        disabledContentColor = palette.searchIcon,
    )
    override val rippleColor: Color = palette.ripple
}

@Immutable
internal data class DockletLobeIconSize(override val iconSize: Dp) : PolkadotIconButtonSize {
    override val padding = PaddingValues((LobeTouchTarget - iconSize) / 2)
}
