package io.paritytech.polkadotapp.feature_connection_status_api.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.paritytech.polkadotapp.common.utils.inFractionalSeconds
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainGlyph
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator.Speed
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicatorsModel
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthItemModel
import kotlinx.collections.immutable.persistentListOf
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import io.paritytech.polkadotapp.common.R as RCommon

/**
 * The "Network Status" breakdown behind the tab bar's connectivity item: one row per monitored chain with
 * its indicator at panel size, its name, and what the indicator is saying alongside the chain's block time.
 */
@Composable
fun ChainHealthPanel(
    modifier: Modifier = Modifier,
    model: ChainHealthIndicatorsModel,
) {
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PolkadotTheme.spacings.mediumIncreased),
            verticalArrangement = Arrangement.spacedBy(PolkadotTheme.spacings.large),
        ) {
            NovaText(
                text = stringResource(RCommon.string.chain_health_panel_title),
                style = PolkadotTheme.typography.title.large,
                color = PolkadotTheme.colors.fg.primary,
            )
            model.chains.forEach { item -> ChainRow(item = item) }
        }
    }
}

@Composable
private fun ChainRow(item: ChainHealthItemModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PolkadotTheme.spacings.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChainIndicator(item = item, indicatorSize = ChainIndicatorSize.Panel)
        Column {
            NovaText(
                text = stringResource(item.glyph.nameRes()),
                style = PolkadotTheme.typography.title.medium,
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

// The design names rows by the chain's role, not by the registry name of whichever network is active.
private fun ChainGlyph.nameRes(): Int = when (this) {
    ChainGlyph.People -> RCommon.string.chain_health_chain_people
    ChainGlyph.AssetHub -> RCommon.string.chain_health_chain_hub
    ChainGlyph.Bulletin -> RCommon.string.chain_health_chain_bulletin
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

private fun Duration.formatSeconds(): String = "%.1f".format(Locale.ROOT, inFractionalSeconds)

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ChainHealthPanelHealthyPreview() {
    PanelPreview(
        ChainHealthIndicator.ConnectionSpeed(Speed.Good),
        ChainHealthIndicator.ConnectionSpeed(Speed.Good),
        ChainHealthIndicator.ConnectionSpeed(Speed.Good),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ChainHealthPanelMixedPreview() {
    PanelPreview(
        ChainHealthIndicator.Healthy,
        ChainHealthIndicator.ConnectionSpeed(Speed.Fair),
        ChainHealthIndicator.Outage(recentBlocks = 3, expectedBlocks = 5),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ChainHealthPanelDisconnectedPreview() {
    PanelPreview(
        ChainHealthIndicator.Disconnected,
        ChainHealthIndicator.Disconnected,
        ChainHealthIndicator.Connecting,
    )
}

@Composable
private fun PanelPreview(people: ChainHealthIndicator, hub: ChainHealthIndicator, bulletin: ChainHealthIndicator) {
    PolkadotTheme {
        ChainHealthPanel(
            modifier = Modifier.background(PolkadotTheme.colors.bg.surface.container),
            model = ChainHealthIndicatorsModel(
                persistentListOf(
                    previewItem("People Chain", ChainGlyph.People, people, 6.seconds),
                    previewItem("Hub Chain", ChainGlyph.AssetHub, hub, 12.seconds),
                    previewItem("Bulletin Chain", ChainGlyph.Bulletin, bulletin, 6.seconds),
                ),
            ),
        )
    }
}

private fun previewItem(name: String, glyph: ChainGlyph, indicator: ChainHealthIndicator, blockTime: Duration) =
    ChainHealthItemModel(
        chainId = name,
        chainName = name,
        glyph = glyph,
        indicator = indicator,
        expectedBlockTime = blockTime,
    )
