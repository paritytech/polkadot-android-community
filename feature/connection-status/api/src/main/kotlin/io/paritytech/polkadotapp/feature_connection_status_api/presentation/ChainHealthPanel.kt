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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainGlyph
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicatorsModel
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthItemModel
import kotlinx.collections.immutable.persistentListOf
import kotlin.math.roundToInt
import kotlin.time.DurationUnit
import io.paritytech.polkadotapp.common.R as RCommon

private const val PERCENT = 100

/**
 * The "Network Status" breakdown behind the tab bar's connectivity item: one row per monitored chain
 * with its indicator at panel size, its name, and what the indicator is saying.
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
        ChainIndicator(modifier = Modifier.clearAndSetSemantics { }, item = item, indicatorSize = ChainIndicatorSize.Panel)
        Column {
            NovaText(
                text = stringResource(item.glyph.nameRes()),
                style = PolkadotTheme.typography.title.medium,
                color = PolkadotTheme.colors.fg.primary,
            )
            NovaText(
                text = item.indicator.summary(),
                style = PolkadotTheme.typography.body.small,
                color = PolkadotTheme.colors.fg.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun ChainHealthIndicator.summary(): String = when (this) {
    is ChainHealthIndicator.Producing -> stringResource(
        RCommon.string.chain_health_summary_producing,
        stringResource(labelRes()),
        (share * PERCENT).roundToInt(),
        blockInterval.toDouble(DurationUnit.SECONDS).roundToInt(),
    )

    ChainHealthIndicator.Outage,
    ChainHealthIndicator.Connecting,
    ChainHealthIndicator.Broken,
    ChainHealthIndicator.NoInternet,
    -> stringResource(labelRes())
}

// The design names rows by the chain's role, not by the registry name of whichever network is active.
private fun ChainGlyph.nameRes(): Int = when (this) {
    ChainGlyph.People -> RCommon.string.chain_health_chain_people
    ChainGlyph.AssetHub -> RCommon.string.chain_health_chain_hub
    ChainGlyph.Bulletin -> RCommon.string.chain_health_chain_bulletin
}

internal fun ChainHealthIndicator.labelRes(): Int = when (this) {
    is ChainHealthIndicator.Producing -> RCommon.string.chain_health_state_connected
    ChainHealthIndicator.Outage -> RCommon.string.chain_health_state_not_producing
    ChainHealthIndicator.Connecting -> RCommon.string.chain_health_state_connecting
    ChainHealthIndicator.Broken -> RCommon.string.chain_health_state_broken
    ChainHealthIndicator.NoInternet -> RCommon.string.chain_health_state_no_internet
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ChainHealthPanelHealthyPreview() {
    PanelPreview(previewProducing(1f), previewProducing(0.93f), previewProducing(0.9f))
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ChainHealthPanelMixedPreview() {
    PanelPreview(previewProducing(0.56f), previewProducing(0.38f), ChainHealthIndicator.Outage)
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ChainHealthPanelDisconnectedPreview() {
    PanelPreview(ChainHealthIndicator.Broken, ChainHealthIndicator.NoInternet, ChainHealthIndicator.Connecting)
}

@Composable
private fun PanelPreview(people: ChainHealthIndicator, hub: ChainHealthIndicator, bulletin: ChainHealthIndicator) {
    PolkadotTheme {
        ChainHealthPanel(
            modifier = Modifier.background(PolkadotTheme.colors.bg.surface.container),
            model = ChainHealthIndicatorsModel(
                persistentListOf(
                    previewItem("People Chain", ChainGlyph.People, people),
                    previewItem("Hub Chain", ChainGlyph.AssetHub, hub),
                    previewItem("Bulletin Chain", ChainGlyph.Bulletin, bulletin),
                ),
            ),
        )
    }
}
