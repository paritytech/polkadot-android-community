package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.components.digitalDollar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.paritytech.polkadotapp.common.utils.CurrencyConfig
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonStyle
import io.paritytech.polkadotapp.design.components.button.default.PolkadotTextButton
import io.paritytech.polkadotapp.design.components.icon.NovaIcon
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.ArrowDownward
import io.paritytech.polkadotapp.design.components.icon.vectors.ArrowUpwards
import io.paritytech.polkadotapp.design.components.spacer.HorizontalSpacer
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.LocalTokenAmountFormatter
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.TokenAmountFormatter
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.RoundPrecision
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.TokenAmountModel
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.components.digitalDollar.holdings.CoinageCompositionBar
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.components.digitalDollar.holdings.CoinageHoldingsList
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.components.digitalDollar.holdings.CoinageLegendSwatch
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.components.digitalDollar.holdings.HoldingColors
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.components.digitalDollar.holdings.HoldingGeometry
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.components.digitalDollar.holdings.rememberBarberPolePhase
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.components.digitalDollar.holdings.rememberHoldingColors
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.models.CoinageCompositionUiModel
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.models.CoinageUiState
import kotlinx.collections.immutable.persistentListOf
import io.paritytech.polkadotapp.common.R as RCommon

/**
 * The total, the partition of it, and the holdings it is made of.
 *
 * The three category figures partition the total exactly, and the bar above them is a picture of the same
 * three buckets in the same order — both are read off one classification, so they cannot contradict each
 * other. Summing the details rows by availability reproduces the figures for the same reason.
 *
 * "Spendable", "Gaining privacy" and "Unavailable" are deliberately not the pallet's words: "recycler" and its
 * relatives stay in code identifiers and never reach the screen, and "Unavailable" avoids promising a remedy
 * for money that is simply in flight or past the age the chain accepts.
 */
@Composable
internal fun CoinageStateCard(
    modifier: Modifier = Modifier,
    state: CoinageUiState.TokensState,
    detailsVisible: Boolean,
    keyVisible: Boolean,
    shareLogsEnabled: Boolean,
    onDetailsToggled: () -> Unit,
    onKeyToggled: () -> Unit,
    onMakeVouchersReadyClick: () -> Unit,
    onShareLogsClick: () -> Unit
) {
    val colors = rememberHoldingColors()
    val stripePhase = rememberBarberPolePhase()

    PolkadotSurface(
        modifier = modifier,
        shape = RoundedCornerShape(HoldingGeometry.containerCorner),
        color = PolkadotTheme.colors.bg.surface.container
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(HoldingGeometry.containerPadding),
            verticalArrangement = Arrangement.spacedBy(HoldingGeometry.containerSpacing)
        ) {
            NovaText(
                text = stringResource(RCommon.string.pocket_coinage_balance_title),
                style = PolkadotTheme.typography.title.medium,
                color = PolkadotTheme.colors.fg.primary
            )

            Headline(total = state.totalBalance)

            CoinageCompositionBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HoldingGeometry.summaryBarHeight),
                composition = state.composition,
                stripePhase = stripePhase,
                colors = colors
            )

            CategoryLegend(state = state, stripePhase = stripePhase, colors = colors)

            PolkadotTextButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(RCommon.string.pocket_coinage_make_vouchers_ready),
                style = PolkadotButtonStyle.secondary(),
                onClick = onMakeVouchersReadyClick
            )

            // Beside the other debug action rather than at the foot of the card: everything below this is
            // the details disclosure, and a button after it would move whenever the list opened.
            if (shareLogsEnabled) {
                PolkadotTextButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(RCommon.string.pocket_coinage_share_logs),
                    style = PolkadotButtonStyle.secondary(),
                    onClick = onShareLogsClick
                )
            }

            DetailsToggle(expanded = detailsVisible, onClick = onDetailsToggled)

            // The key belongs to the details, not to the summary: it explains marks that are only on screen
            // while the list is open, so it goes away with them.
            AnimatedVisibility(visible = detailsVisible) {
                Column(verticalArrangement = Arrangement.spacedBy(HoldingGeometry.containerSpacing)) {
                    CoinageHoldingsList(
                        modifier = Modifier.fillMaxWidth(),
                        holdings = state.holdings
                    )

                    CoinageKey(
                        expanded = keyVisible,
                        onToggle = onKeyToggled,
                        colors = colors,
                        stripePhase = stripePhase
                    )
                }
            }
        }
    }
}

/** The amount carries no symbol of its own; the symbol appears once here, small and beside it. */
@Composable
private fun Headline(total: TokenAmountModel) {
    val formatter = LocalTokenAmountFormatter.current

    Column {
        NovaText(
            text = stringResource(RCommon.string.pocket_coinage_total_balance),
            style = PolkadotTheme.typography.body.small,
            color = PolkadotTheme.colors.fg.secondary
        )

        Row(horizontalArrangement = Arrangement.spacedBy(HoldingGeometry.headlineSpacing)) {
            NovaText(
                modifier = Modifier.alignByBaseline(),
                text = formatter.formatTokenAmount(total, RoundPrecision.FIAT, withSymbol = false),
                maxLines = 1,
                style = PolkadotTheme.typography.headline.large,
                color = PolkadotTheme.colors.fg.primary
            )
            NovaText(
                modifier = Modifier.alignByBaseline(),
                text = CurrencyConfig.symbol,
                style = PolkadotTheme.typography.body.small,
                color = PolkadotTheme.colors.fg.secondary
            )
        }
    }
}

/**
 * Two rows rather than three stacked columns, so a label that wraps cannot push its own value onto a
 * different line. Every cell takes an equal third, leading-aligned.
 */
@Composable
private fun CategoryLegend(
    state: CoinageUiState.TokensState,
    stripePhase: androidx.compose.runtime.State<Float>,
    colors: HoldingColors
) {
    val formatter = LocalTokenAmountFormatter.current

    Column(verticalArrangement = Arrangement.spacedBy(HoldingGeometry.legendRowSpacing)) {
        Row(horizontalArrangement = Arrangement.spacedBy(HoldingGeometry.legendColumnSpacing)) {
            LegendLabel(
                modifier = Modifier.weight(1f),
                label = stringResource(RCommon.string.pocket_coinage_spendable),
                fill = colors.spendable,
                gainingPrivacy = false,
                stripePhase = stripePhase,
                colors = colors
            )
            LegendLabel(
                modifier = Modifier.weight(1f),
                label = stringResource(RCommon.string.pocket_coinage_gaining_privacy),
                fill = null,
                gainingPrivacy = true,
                stripePhase = stripePhase,
                colors = colors
            )
            LegendLabel(
                modifier = Modifier.weight(1f),
                label = stringResource(RCommon.string.pocket_coinage_unavailable),
                fill = colors.notSpendable,
                gainingPrivacy = false,
                stripePhase = stripePhase,
                colors = colors
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(HoldingGeometry.legendColumnSpacing)) {
            listOf(state.spendableBalance, state.gainingPrivacyBalance, state.unavailableBalance).forEach { amount ->
                NovaText(
                    modifier = Modifier.weight(1f),
                    text = formatter.formatTokenAmount(amount, RoundPrecision.FIAT, withSymbol = false),
                    maxLines = 1,
                    style = PolkadotTheme.typography.title.small,
                    color = PolkadotTheme.colors.fg.primary
                )
            }
        }
    }
}

@Composable
private fun LegendLabel(
    modifier: Modifier = Modifier,
    label: String,
    fill: Color?,
    gainingPrivacy: Boolean,
    stripePhase: androidx.compose.runtime.State<Float>,
    colors: HoldingColors
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        CoinageLegendSwatch(fill = fill, gainingPrivacy = gainingPrivacy, stripePhase = stripePhase, colors = colors)

        HorizontalSpacer { HoldingGeometry.legendSwatchLabelSpacing }

        NovaText(
            text = label,
            maxLines = 1,
            style = PolkadotTheme.typography.body.small,
            color = PolkadotTheme.colors.fg.secondary
        )
    }
}

@Composable
private fun DetailsToggle(expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        NovaIcon(
            modifier = Modifier.size(HoldingGeometry.legendSwatch),
            imageVector = if (expanded) NovaIcons.ArrowUpwards else NovaIcons.ArrowDownward,
            tint = PolkadotTheme.colors.fg.secondary
        )

        HorizontalSpacer { HoldingGeometry.legendSwatchLabelSpacing }

        NovaText(
            text = stringResource(RCommon.string.pocket_coinage_show_details),
            style = PolkadotTheme.typography.title.small,
            color = PolkadotTheme.colors.fg.primary
        )
    }
}

@Preview
@Composable
private fun CoinageStateCardPreview() {
    CompositionLocalProvider(LocalTokenAmountFormatter provides TokenAmountFormatter.mocked) {
        PolkadotTheme {
            CoinageStateCard(
                modifier = Modifier.fillMaxWidth(),
                state = CoinageUiState.TokensState(
                    totalBalance = TokenAmountModel.mock,
                    spendableBalance = TokenAmountModel.mock,
                    gainingPrivacyBalance = TokenAmountModel.mock,
                    unavailableBalance = TokenAmountModel.mock,
                    composition = CoinageCompositionUiModel(0.5f, 0.3f, 0.2f),
                    holdings = persistentListOf()
                ),
                detailsVisible = false,
                keyVisible = false,
                shareLogsEnabled = true,
                onDetailsToggled = {},
                onKeyToggled = {},
                onMakeVouchersReadyClick = {},
                onShareLogsClick = {}
            )
        }
    }
}
