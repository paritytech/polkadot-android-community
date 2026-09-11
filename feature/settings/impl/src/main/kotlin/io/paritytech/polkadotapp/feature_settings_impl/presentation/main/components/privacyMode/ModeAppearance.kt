package io.paritytech.polkadotapp.feature_settings_impl.presentation.main.components.privacyMode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.BoltCircleFilled
import io.paritytech.polkadotapp.design.components.icon.vectors.ShieldHalf
import io.paritytech.polkadotapp.design.components.icon.vectors.VisibilityOffOutlined
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.RecyclingStrategyType
import io.paritytech.polkadotapp.common.R as RCommon

@Immutable
internal data class ModeAppearance(
    val label: String,
    val description: String,
    val accessibilityDescription: String,
    val icon: ImageVector,
    val colors: ModeColors,
    val glowSize: Dp,
    val glowCornerRadius: Dp
)

@Composable
internal fun RecyclingStrategyType.appearance(): ModeAppearance = when (this) {
    RecyclingStrategyType.MIN_PRIVACY -> ModeAppearance(
        label = stringResource(RCommon.string.payment_privacy_mode_fastest_label),
        description = stringResource(RCommon.string.payment_privacy_mode_fastest_description),
        accessibilityDescription = stringResource(RCommon.string.payment_privacy_mode_fastest_accessibility),
        icon = NovaIcons.BoltCircleFilled,
        colors = PrivacyModeColors.Fastest,
        glowSize = GLOW_SIZE,
        glowCornerRadius = GLOW_CORNER_RADIUS
    )

    RecyclingStrategyType.BALANCED -> ModeAppearance(
        label = stringResource(RCommon.string.payment_privacy_mode_balanced_label),
        description = stringResource(RCommon.string.payment_privacy_mode_balanced_description),
        accessibilityDescription = stringResource(RCommon.string.payment_privacy_mode_balanced_accessibility),
        icon = NovaIcons.ShieldHalf,
        colors = PrivacyModeColors.Balanced,
        glowSize = BALANCED_GLOW_SIZE,
        glowCornerRadius = BALANCED_GLOW_CORNER_RADIUS
    )

    RecyclingStrategyType.MAX_PRIVACY -> ModeAppearance(
        label = stringResource(RCommon.string.payment_privacy_mode_most_private_label),
        description = stringResource(RCommon.string.payment_privacy_mode_most_private_description),
        accessibilityDescription = stringResource(
            RCommon.string.payment_privacy_mode_most_private_accessibility
        ),
        icon = NovaIcons.VisibilityOffOutlined,
        colors = PrivacyModeColors.MostPrivate,
        glowSize = GLOW_SIZE,
        glowCornerRadius = GLOW_CORNER_RADIUS
    )
}

// [selection] is the animated 0..1 selectedness of the mode, so a mode lights up and dims in step with the
// circle growing and shrinking instead of switching colour a frame apart from it.
internal fun ModeAppearance.circleBrush(selection: Float): Brush = Brush.verticalGradient(
    listOf(
        lerp(colors.unselected.fillTop, colors.selected.fillTop, selection),
        lerp(colors.unselected.fillBottom, colors.selected.fillBottom, selection)
    )
)

internal fun ModeAppearance.circleBorderBrush(selection: Float): Brush = Brush.verticalGradient(
    listOf(
        lerp(colors.unselected.rimTop, colors.selected.rimTop, selection),
        lerp(colors.unselected.rimBottom, colors.selected.rimBottom, selection)
    )
)

// A dragged circle takes on the mode it is nearest to, so it changes appearance mid-gesture. Swapping the
// colours in a single frame is what reads as a jump; blending them lets the colour travel together with the
// glyph cross-fade in [ModeCircle]. [fraction] is 0 while the circle still shows [previous] and 1 once it
// has fully become this mode.
internal fun ModeAppearance.blendedFrom(previous: ModeAppearance, fraction: Float): ModeAppearance =
    if (fraction >= 1f) this else copy(colors = previous.colors.blendedTo(colors, fraction))
