package io.paritytech.polkadotapp.design.components.docklet

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonColors
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonStyle
import io.paritytech.polkadotapp.design.components.button.icon.PolkadotIconButtonSize

internal val LobeTouchTarget = 48.dp

@Immutable
object DockletFieldButtonStyle : PolkadotButtonStyle {
    override val colors = PolkadotButtonColors(
        containerBrush = SolidColor(DockletColors.FieldFill),
        contentColor = DockletColors.SearchIcon,
        disabledContainerBrush = SolidColor(DockletColors.FieldFill),
        disabledContentColor = DockletColors.SearchIcon,
    )
    override val rippleColor: Color = DockletColors.Ripple
}

@Immutable
internal data class DockletLobeIconSize(override val iconSize: Dp) : PolkadotIconButtonSize {
    override val padding = PaddingValues((LobeTouchTarget - iconSize) / 2)
}
