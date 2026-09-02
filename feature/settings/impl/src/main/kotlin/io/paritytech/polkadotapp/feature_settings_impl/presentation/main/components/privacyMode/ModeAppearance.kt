package io.paritytech.polkadotapp.feature_settings_impl.presentation.main.components.privacyMode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.Bolt
import io.paritytech.polkadotapp.design.components.icon.vectors.ShieldCheck
import io.paritytech.polkadotapp.design.components.icon.vectors.ShieldLock
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.RecyclingStrategyType
import io.paritytech.polkadotapp.common.R as RCommon

@Immutable
internal data class ModeAppearance(
    val label: String,
    val description: String,
    val accessibilityDescription: String,
    val icon: ImageVector,
    /** Fills the circle and tints the selection arrows. */
    val accentColor: Color,
    val iconColor: Color,
    val labelColor: Color
)

@Composable
internal fun RecyclingStrategyType.appearance(): ModeAppearance {
    // Every accent below is the same value in all palettes, so each glyph colour is chosen once and holds in
    // both themes: amber only carries a dark glyph, green and violet only a light one.
    val onDarkAccent = PolkadotTheme.colors.fg.staticWhite

    return when (this) {
        RecyclingStrategyType.MIN_PRIVACY -> ModeAppearance(
            label = stringResource(RCommon.string.payment_privacy_mode_fastest_label),
            description = stringResource(RCommon.string.payment_privacy_mode_fastest_description),
            accessibilityDescription = stringResource(RCommon.string.payment_privacy_mode_fastest_accessibility),
            icon = NovaIcons.Bolt,
            accentColor = PolkadotTheme.colors.bg.status.warning,
            iconColor = PolkadotTheme.colors.avatar.bg.onyx,
            // Amber is too light to read as a label on a light background; fg.warning is the darkened variant
            // the palette keeps for exactly that.
            labelColor = PolkadotTheme.colors.fg.warning
        )

        RecyclingStrategyType.BALANCED -> ModeAppearance(
            label = stringResource(RCommon.string.payment_privacy_mode_balanced_label),
            description = stringResource(RCommon.string.payment_privacy_mode_balanced_description),
            accessibilityDescription = stringResource(RCommon.string.payment_privacy_mode_balanced_accessibility),
            icon = NovaIcons.ShieldCheck,
            accentColor = PolkadotTheme.colors.bg.status.success,
            iconColor = onDarkAccent,
            labelColor = PolkadotTheme.colors.fg.success
        )

        RecyclingStrategyType.MAX_PRIVACY -> ModeAppearance(
            label = stringResource(RCommon.string.payment_privacy_mode_most_private_label),
            description = stringResource(RCommon.string.payment_privacy_mode_most_private_description),
            accessibilityDescription = stringResource(
                RCommon.string.payment_privacy_mode_most_private_accessibility
            ),
            icon = NovaIcons.ShieldLock,
            accentColor = PolkadotTheme.colors.avatar.bg.amethyst,
            iconColor = onDarkAccent,
            labelColor = PolkadotTheme.colors.avatar.bg.amethyst
        )
    }
}
