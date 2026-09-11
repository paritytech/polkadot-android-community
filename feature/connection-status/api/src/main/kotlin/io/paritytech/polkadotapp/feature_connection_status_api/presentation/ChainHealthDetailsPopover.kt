package io.paritytech.polkadotapp.feature_connection_status_api.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator.Speed
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthItemModel
import kotlin.time.Duration
import io.paritytech.polkadotapp.common.R as RCommon

private val POPUP_MARGIN = 12.dp
private val POPUP_GAP = 8.dp
private val POPUP_ELEVATION = 12.dp
private const val MILLIS_PER_SECOND = 1000.0

/** Tap-to-open summary of a single chain: what its indicator is saying, and the chain's block time. */
@Composable
fun ChainHealthDetailsPopover(
    expanded: Boolean,
    item: ChainHealthItemModel,
    onDismiss: () -> Unit,
) {
    if (expanded) {
        val gapPx = with(LocalDensity.current) { POPUP_GAP.roundToPx() }
        val positionProvider = remember(gapPx) { BelowAnchorPositionProvider(gapPx = gapPx) }

        Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = true),
        ) {
            PolkadotSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = POPUP_MARGIN),
                shape = PolkadotTheme.shapes.large,
                color = PolkadotTheme.colors.bg.surface.container,
                shadowElevation = POPUP_ELEVATION,
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = PolkadotTheme.spacings.medium,
                        vertical = PolkadotTheme.spacings.small,
                    ),
                ) {
                    NovaText(
                        text = item.chainName,
                        style = PolkadotTheme.typography.body.mediumEmphasized,
                        color = PolkadotTheme.colors.fg.primary,
                    )
                    NovaText(
                        text = stringResource(
                            RCommon.string.chain_health_summary,
                            stringResource(item.indicator.labelRes()),
                            item.expectedBlockTime.formatSeconds(),
                        ),
                        style = PolkadotTheme.typography.body.small,
                        color = PolkadotTheme.colors.fg.secondary,
                    )
                }
            }
        }
    }
}

private fun ChainHealthIndicator.labelRes(): Int = when (this) {
    ChainHealthIndicator.Healthy -> RCommon.string.chain_health_state_speed_high
    is ChainHealthIndicator.Outage -> RCommon.string.chain_health_state_not_producing
    is ChainHealthIndicator.ConnectionSpeed -> when (speed) {
        Speed.Good -> RCommon.string.chain_health_state_speed_good
        Speed.Fair -> RCommon.string.chain_health_state_speed_fair
        Speed.Low -> RCommon.string.chain_health_state_speed_low
    }
    ChainHealthIndicator.Connecting -> RCommon.string.chain_health_state_connecting
    ChainHealthIndicator.Disconnected -> RCommon.string.chain_health_state_broken
}

private fun Duration.formatSeconds(): String = "%.1fs".format(inWholeMilliseconds / MILLIS_PER_SECOND)

/**
 * Positions the popup just below the tapped anchor. The anchor already sits below the system status
 * bar, so the popup never crosses it; horizontally it is right-aligned within the window with a margin.
 */
private class BelowAnchorPositionProvider(
    private val gapPx: Int,
) : PopupPositionProvider {
    // Full-width content (margins come from the surface's own padding); only the vertical anchor
    // matters — drop just below the tapped icon, which already sits below the system status bar.
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(x = 0, y = anchorBounds.bottom + gapPx)
}
