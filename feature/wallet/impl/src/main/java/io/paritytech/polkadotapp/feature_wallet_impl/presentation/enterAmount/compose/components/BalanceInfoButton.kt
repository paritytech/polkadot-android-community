package io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonColors
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonShape
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonStyle
import io.paritytech.polkadotapp.design.components.button.icon.PolkadotIconButton
import io.paritytech.polkadotapp.design.components.button.icon.PolkadotIconButtonSize
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.Info
import io.paritytech.polkadotapp.design.theme.PolkadotTheme

@Composable
internal fun BalanceInfoButton(description: String, onClick: () -> Unit) {
    PolkadotIconButton(
        modifier = Modifier.semantics { contentDescription = description },
        icon = NovaIcons.Info,
        onClick = onClick,
        style = secondaryGlyphStyle(),
        size = PolkadotIconButtonSize.extraSmall(),
        shape = PolkadotButtonShape.pill
    )
}

@Composable
private fun secondaryGlyphStyle(): PolkadotButtonStyle {
    val colors = PolkadotButtonColors(
        containerBrush = SolidColor(Color.Transparent),
        contentColor = PolkadotTheme.colors.fg.secondary,
        disabledContainerBrush = SolidColor(Color.Transparent),
        disabledContentColor = PolkadotTheme.colors.fg.disabled
    )

    return remember(colors) {
        object : PolkadotButtonStyle {
            override val colors = colors
            override val rippleColor = colors.contentColor
        }
    }
}
