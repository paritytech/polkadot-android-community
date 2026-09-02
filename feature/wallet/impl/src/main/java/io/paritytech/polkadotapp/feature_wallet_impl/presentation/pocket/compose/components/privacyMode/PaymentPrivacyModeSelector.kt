package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.components.privacyMode

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.paritytech.polkadotapp.design.components.icon.NovaIcon
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.Bolt
import io.paritytech.polkadotapp.design.components.icon.vectors.ShieldCheck
import io.paritytech.polkadotapp.design.components.icon.vectors.ShieldLock
import io.paritytech.polkadotapp.design.components.spacer.HorizontalSpacer
import io.paritytech.polkadotapp.design.components.spacer.VerticalSpacer
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.RecyclingStrategyType
import io.paritytech.polkadotapp.common.R as RCommon

/**
 * The primary operating-mode control of the payment system: how much of what the user receives is held back
 * to gain privacy before it can be spent again.
 *
 * Quota is deliberately absent from the copy — it is managed automatically and the user cannot act on it.
 */
@Composable
fun PaymentPrivacyModeSelector(
    modifier: Modifier = Modifier,
    selectedMode: RecyclingStrategyType,
    onModeSelected: (RecyclingStrategyType) -> Unit
) {
    PolkadotSurface(
        modifier = modifier,
        shape = RoundedCornerShape(PolkadotTheme.radii.large),
        color = PolkadotTheme.colors.bg.surface.container
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PolkadotTheme.spacings.mediumIncreased)
        ) {
            Header()

            VerticalSpacer { large }

            ModeRow(
                selectedMode = selectedMode,
                onModeSelected = onModeSelected
            )

            VerticalSpacer { small }
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.Top) {
        NovaIcon(
            modifier = Modifier.size(HEADER_ICON_SIZE),
            imageVector = NovaIcons.ShieldLock,
            tint = PolkadotTheme.colors.fg.secondary
        )

        HorizontalSpacer { small }

        Column(modifier = Modifier.weight(1f)) {
            NovaText(
                text = stringResource(RCommon.string.payment_privacy_mode_title),
                style = PolkadotTheme.typography.title.small,
                color = PolkadotTheme.colors.fg.primary
            )

            VerticalSpacer { extraTiny }

            NovaText(
                text = stringResource(RCommon.string.payment_privacy_mode_subtitle),
                style = PolkadotTheme.typography.body.small,
                color = PolkadotTheme.colors.fg.secondary
            )
        }
    }
}

@Composable
private fun ModeRow(
    selectedMode: RecyclingStrategyType,
    onModeSelected: (RecyclingStrategyType) -> Unit
) {
    val modes = RecyclingStrategyType.entries

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalAlignment = Alignment.Top
    ) {
        modes.forEachIndexed { index, mode ->
            ModeItem(
                mode = mode,
                isSelected = mode == selectedMode,
                hasConnectorBefore = index > 0,
                hasConnectorAfter = index < modes.lastIndex,
                onClick = { onModeSelected(mode) }
            )
        }
    }
}

@Composable
private fun RowScope.ModeItem(
    mode: RecyclingStrategyType,
    isSelected: Boolean,
    hasConnectorBefore: Boolean,
    hasConnectorAfter: Boolean,
    onClick: () -> Unit
) {
    val appearance = mode.appearance()
    // The column is the touch target, but a ripple across label and description would read as a card press.
    // The indication is handed to the circle instead, which is the thing the user is actually choosing.
    val interactionSource = remember { MutableInteractionSource() }

    val itemDescription = appearance.accessibilityDescription
    val itemState = stringResource(
        if (isSelected) {
            RCommon.string.payment_privacy_mode_state_selected
        } else {
            RCommon.string.payment_privacy_mode_state_not_selected
        }
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .selectable(
                selected = isSelected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick
            )
            .semantics(mergeDescendants = true) {
                contentDescription = itemDescription
                stateDescription = itemState
            }
            .padding(vertical = PolkadotTheme.spacings.extraSmall),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NovaText(
            text = appearance.label,
            style = PolkadotTheme.typography.title.tiny,
            color = appearance.labelColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        VerticalSpacer { small }

        ModeIndicator(
            appearance = appearance,
            isSelected = isSelected,
            interactionSource = interactionSource,
            hasConnectorBefore = hasConnectorBefore,
            hasConnectorAfter = hasConnectorAfter
        )

        VerticalSpacer { small }

        NovaText(
            text = appearance.description,
            style = PolkadotTheme.typography.label.small,
            color = PolkadotTheme.colors.fg.secondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ModeIndicator(
    appearance: ModeAppearance,
    isSelected: Boolean,
    interactionSource: MutableInteractionSource,
    hasConnectorBefore: Boolean,
    hasConnectorAfter: Boolean
) {
    // Plain Compose animation, so a device with animations switched off snaps instead of tweening.
    val circleSize by animateDpAsState(
        targetValue = if (isSelected) SELECTED_CIRCLE_SIZE else CIRCLE_SIZE,
        animationSpec = tween(durationMillis = SELECTION_ANIMATION_MILLIS, easing = FastOutSlowInEasing),
        label = "modeCircleSize"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SELECTION_RING_SIZE),
        contentAlignment = Alignment.Center
    ) {
        // The connector is laid out rather than drawn: the two weighted halves keep the circle centred in the
        // cell, and neighbouring cells are the same width, so the segments meet into one continuous line.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Connector(isVisible = hasConnectorBefore)

            Box(modifier = Modifier.width(circleSize + CONNECTOR_GAP * 2))

            Connector(isVisible = hasConnectorAfter)
        }

        if (isSelected) {
            PolkadotSurface(
                modifier = Modifier.size(SELECTION_RING_SIZE),
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(SELECTION_RING_WIDTH, appearance.accentColor)
            ) {}
        }

        PolkadotSurface(
            modifier = Modifier.size(circleSize),
            shape = CircleShape,
            color = appearance.accentColor
        ) {
            // PolkadotSurface propagates its minimum constraints, so a size on the icon itself would be
            // clamped straight back up to the circle. This Box absorbs the minimum and lets the inset stand.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .indication(interactionSource, LocalIndication.current),
                contentAlignment = Alignment.Center
            ) {
                NovaIcon(
                    modifier = Modifier.size(circleSize * MODE_ICON_SIZE_FRACTION),
                    imageVector = appearance.icon,
                    tint = appearance.iconColor
                )
            }
        }
    }
}

@Composable
private fun RowScope.Connector(isVisible: Boolean) {
    PolkadotSurface(
        modifier = Modifier
            .weight(1f)
            .height(CONNECTOR_THICKNESS),
        color = if (isVisible) PolkadotTheme.colors.stroke.secondary else Color.Transparent
    ) {}
}

private data class ModeAppearance(
    val label: String,
    val description: String,
    val accessibilityDescription: String,
    val icon: ImageVector,
    /** Fills the circle and draws the selection ring. */
    val accentColor: Color,
    val iconColor: Color,
    val labelColor: Color
)

@Composable
private fun RecyclingStrategyType.appearance(): ModeAppearance {
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

private val CIRCLE_SIZE = 48.dp

private val SELECTED_CIRCLE_SIZE = 56.dp

private val SELECTION_RING_SIZE = 68.dp

private val SELECTION_RING_WIDTH = 2.dp

/** Share of the circle the glyph takes up; the rest is the inset around it. */
private const val MODE_ICON_SIZE_FRACTION = 0.7f

private val HEADER_ICON_SIZE = 24.dp

private val CONNECTOR_THICKNESS = 1.dp

private val CONNECTOR_GAP = 6.dp

private const val SELECTION_ANIMATION_MILLIS = 200

@Preview
@Composable
private fun PaymentPrivacyModeSelectorPreview() {
    PolkadotTheme {
        Column(
            modifier = Modifier.padding(PolkadotTheme.spacings.mediumIncreased)
        ) {
            RecyclingStrategyType.entries.forEach { mode ->
                PaymentPrivacyModeSelector(
                    modifier = Modifier.fillMaxWidth(),
                    selectedMode = mode,
                    onModeSelected = {}
                )

                VerticalSpacer { mediumIncreased }
            }
        }
    }
}
