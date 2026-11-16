package io.paritytech.polkadotapp.feature_settings_impl.presentation.main.components.privacyMode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.paritytech.polkadotapp.design.components.spacer.HorizontalSpacer
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme

@Composable
internal fun SelectedModeDescription(appearance: ModeAppearance) {
    PolkadotSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DESCRIPTION_RADIUS),
        color = PolkadotTheme.colors.bg.surface.nested
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = PolkadotTheme.spacings.mediumIncreased,
                    vertical = PolkadotTheme.spacings.extraMedium
                ),
            verticalArrangement = Arrangement.spacedBy(PolkadotTheme.spacings.extraSmall)
        ) {
            NovaText(
                text = appearance.label,
                style = PolkadotTheme.typography.title.medium,
                color = PolkadotTheme.colors.fg.primary
            )

            appearance.bullets.forEach { bullet ->
                BulletItem(text = bullet)
            }
        }
    }
}

@Composable
private fun BulletItem(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        HorizontalSpacer { tiny }

        NovaText(
            text = "•",
            style = PolkadotTheme.typography.paragraph.medium,
            color = PolkadotTheme.colors.fg.secondary
        )

        HorizontalSpacer { small }

        NovaText(
            modifier = Modifier.weight(1f),
            text = text,
            style = PolkadotTheme.typography.paragraph.medium,
            color = PolkadotTheme.colors.fg.secondary
        )
    }
}

private val DESCRIPTION_RADIUS = 12.dp
