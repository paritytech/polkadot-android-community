package io.paritytech.polkadotapp.feature_videogame_impl.presentation.play.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.paritytech.polkadotapp.design.components.icon.NovaIcon
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_videogame_impl.presentation.compose.components.icons.CloudTooltipShape

@Composable
fun VideoGameTooltip(
    imageVector: ImageVector?,
    text: String,
    modifier: Modifier = Modifier
) {
    PolkadotSurface(
        modifier = modifier,
        shape = CloudTooltipShape,
        color = PolkadotTheme.colors.bg.surface.containerInverted,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = PolkadotTheme.spacings.extraMedium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PolkadotTheme.spacings.small)
        ) {
            if (imageVector != null) {
                NovaIcon(
                    imageVector = imageVector,
                    tint = PolkadotTheme.colors.fg.primaryInverted,
                )
            }

            NovaText(
                text = text,
                style = PolkadotTheme.typography.title.medium,
                color = PolkadotTheme.colors.fg.primaryInverted,
                softWrap = false,
                maxLines = 1,
            )
        }
    }
}
