@file:OptIn(ExperimentalMaterial3Api::class)

package io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount.compose.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.paritytech.polkadotapp.design.components.bottomsheet.NovaBottomSheetDefaults
import io.paritytech.polkadotapp.design.components.bottomsheet.NovaBottomSheetSurface
import io.paritytech.polkadotapp.design.components.bottomsheet.NovaModalBottomSheet
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonShape
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonStyle
import io.paritytech.polkadotapp.design.components.button.default.PolkadotTextButton
import io.paritytech.polkadotapp.design.components.button.icon.PolkadotIconButton
import io.paritytech.polkadotapp.design.components.button.icon.PolkadotIconButtonSize
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.ArrowLeft
import io.paritytech.polkadotapp.design.components.spacer.VerticalSpacer
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.LocalTokenAmountFormatter
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.TokenAmountFormatter
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.RoundPrecision
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.TokenAmountModel
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount.BalanceBreakdownUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import io.paritytech.polkadotapp.common.R as RCommon

internal sealed interface BalanceDetailsPage {
    data object Details : BalanceDetailsPage

    data object Ready : BalanceDetailsPage

    data object Clearing : BalanceDetailsPage
}

// page outlives isVisible so the closing animation does not blank the content out.
@Immutable
internal data class BalanceDetailsSheetState(
    val isVisible: Boolean,
    val page: BalanceDetailsPage
) {
    companion object {
        val CLOSED = BalanceDetailsSheetState(isVisible = false, page = BalanceDetailsPage.Details)
    }
}

@Composable
internal fun BalanceDetailsBottomSheet(
    state: BalanceDetailsSheetState,
    breakdown: BalanceBreakdownUiModel,
    onPageChange: (BalanceDetailsPage) -> Unit,
    onDismissRequest: () -> Unit
) {
    NovaModalBottomSheet(
        isVisible = state.isVisible,
        onDismissRequest = onDismissRequest
    ) {
        AnimatedContent(
            targetState = state.page,
            transitionSpec = { NovaBottomSheetDefaults.PAGE_TRANSITION_SPEC }
        ) { page ->
            when (page) {
                BalanceDetailsPage.Details -> BalanceDetailsContent(
                    breakdown = breakdown,
                    onTermClick = onPageChange,
                    onDoneClick = onDismissRequest
                )

                BalanceDetailsPage.Ready -> BalanceTermContent(
                    title = stringResource(RCommon.string.send_enter_amount_balance_ready_explainer_title),
                    paragraphs = rememberParagraphs(
                        RCommon.string.send_enter_amount_balance_ready_explainer_first,
                        RCommon.string.send_enter_amount_balance_ready_explainer_second
                    ),
                    onBackClick = { onPageChange(BalanceDetailsPage.Details) }
                )

                BalanceDetailsPage.Clearing -> BalanceTermContent(
                    title = stringResource(RCommon.string.send_enter_amount_balance_clearing_explainer_title),
                    paragraphs = rememberParagraphs(
                        RCommon.string.send_enter_amount_balance_clearing_explainer_first,
                        RCommon.string.send_enter_amount_balance_clearing_explainer_second,
                        RCommon.string.send_enter_amount_balance_clearing_explainer_third
                    ),
                    onBackClick = { onPageChange(BalanceDetailsPage.Details) }
                )
            }
        }
    }
}

@Composable
private fun rememberParagraphs(@StringRes vararg ids: Int): ImmutableList<String> {
    val paragraphs = ids.map { stringResource(it) }

    return remember(paragraphs) { paragraphs.toImmutableList() }
}

@Composable
private fun BalanceDetailsContent(
    breakdown: BalanceBreakdownUiModel,
    onTermClick: (BalanceDetailsPage) -> Unit,
    onDoneClick: () -> Unit
) {
    val formatter = LocalTokenAmountFormatter.current
    val total = remember(breakdown, formatter) { formatter.formatTokenAmount(breakdown.total, RoundPrecision.FIAT) }
    val ready = remember(breakdown, formatter) { formatter.formatTokenAmount(breakdown.ready, RoundPrecision.FIAT) }
    val clearing = remember(breakdown, formatter) { formatter.formatTokenAmount(breakdown.clearing, RoundPrecision.FIAT) }

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetTitle(
            title = stringResource(RCommon.string.send_enter_amount_balance_details_title),
            subtitle = stringResource(RCommon.string.send_enter_amount_balance_details_subtitle)
        )

        PolkadotSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PolkadotTheme.spacings.large),
            shape = PolkadotTheme.shapes.large,
            color = PolkadotTheme.colors.bg.surface.nested
        ) {
            Column(
                modifier = Modifier.padding(PolkadotTheme.spacings.large),
                verticalArrangement = Arrangement.spacedBy(PolkadotTheme.spacings.large)
            ) {
                BalanceRow(
                    title = stringResource(RCommon.string.send_enter_amount_balance_total_title),
                    caption = stringResource(RCommon.string.send_enter_amount_balance_total_caption),
                    amount = total,
                    onInfoClick = null
                )

                BalanceRow(
                    title = stringResource(RCommon.string.send_enter_amount_balance_ready_title),
                    caption = stringResource(RCommon.string.send_enter_amount_balance_ready_caption),
                    amount = ready,
                    onInfoClick = { onTermClick(BalanceDetailsPage.Ready) }
                )

                BalanceRow(
                    title = stringResource(RCommon.string.send_enter_amount_balance_clearing_title),
                    caption = stringResource(RCommon.string.send_enter_amount_balance_clearing_caption),
                    amount = clearing,
                    onInfoClick = { onTermClick(BalanceDetailsPage.Clearing) }
                )
            }
        }

        PolkadotTextButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PolkadotTheme.spacings.mediumIncreased),
            text = stringResource(RCommon.string.common_got_it),
            style = PolkadotButtonStyle.tertiary(),
            shape = PolkadotButtonShape.pill,
            onClick = onDoneClick
        )
    }
}

@Composable
private fun BalanceRow(
    title: String,
    caption: String,
    amount: String,
    onInfoClick: (() -> Unit)?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                horizontalArrangement = Arrangement.spacedBy(PolkadotTheme.spacings.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NovaText(
                    modifier = Modifier.weight(1f, fill = false),
                    text = title,
                    style = PolkadotTheme.typography.title.medium,
                    color = PolkadotTheme.colors.fg.primary
                )

                if (onInfoClick != null) {
                    BalanceInfoButton(
                        description = stringResource(
                            RCommon.string.send_enter_amount_balance_term_info_action,
                            title
                        ),
                        onClick = onInfoClick
                    )
                }
            }

            NovaText(
                text = amount,
                style = PolkadotTheme.typography.title.medium,
                color = PolkadotTheme.colors.fg.primary
            )
        }

        NovaText(
            text = caption,
            style = PolkadotTheme.typography.body.medium,
            color = PolkadotTheme.colors.fg.secondary
        )
    }
}

@Composable
private fun BalanceTermContent(
    title: String,
    paragraphs: ImmutableList<String>,
    onBackClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(
                horizontal = PolkadotTheme.spacings.tiny,
                vertical = PolkadotTheme.spacings.small
            )
        ) {
            PolkadotIconButton(
                icon = NovaIcons.ArrowLeft,
                onClick = onBackClick,
                style = PolkadotButtonStyle.ghost(),
                size = PolkadotIconButtonSize.mediumIncreased(),
                shape = PolkadotButtonShape.pill
            )
        }

        SheetTitle(title = title, subtitle = null)

        Column(
            modifier = Modifier.padding(horizontal = PolkadotTheme.spacings.large),
            verticalArrangement = Arrangement.spacedBy(PolkadotTheme.spacings.small)
        ) {
            paragraphs.forEach { paragraph ->
                NovaText(
                    text = paragraph,
                    style = PolkadotTheme.typography.paragraph.large,
                    color = PolkadotTheme.colors.fg.secondary
                )
            }
        }

        VerticalSpacer { extraLargeIncreased }
    }
}

@Composable
private fun SheetTitle(title: String, subtitle: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = PolkadotTheme.spacings.large,
                vertical = PolkadotTheme.spacings.mediumIncreased
            ),
        verticalArrangement = Arrangement.spacedBy(PolkadotTheme.spacings.small)
    ) {
        NovaText(
            modifier = Modifier.fillMaxWidth(),
            text = title,
            style = PolkadotTheme.typography.headline.small,
            color = PolkadotTheme.colors.fg.primary
        )

        if (subtitle != null) {
            NovaText(
                modifier = Modifier.fillMaxWidth(),
                text = subtitle,
                style = PolkadotTheme.typography.paragraph.large,
                color = PolkadotTheme.colors.fg.primary
            )
        }
    }
}

@Preview
@Preview(widthDp = 320, fontScale = 2f)
@Composable
private fun BalanceDetailsContentPreview() {
    CompositionLocalProvider(LocalTokenAmountFormatter provides TokenAmountFormatter.mocked) {
        PolkadotTheme {
            NovaBottomSheetSurface {
                BalanceDetailsContent(
                    breakdown = BalanceBreakdownUiModel(
                        total = TokenAmountModel.mock,
                        ready = TokenAmountModel.mock,
                        clearing = TokenAmountModel.mock
                    ),
                    onTermClick = {},
                    onDoneClick = {}
                )
            }
        }
    }
}

@Preview
@Preview(widthDp = 320, fontScale = 2f)
@Composable
private fun BalanceTermContentPreview() {
    PolkadotTheme {
        NovaBottomSheetSurface {
            BalanceTermContent(
                title = stringResource(RCommon.string.send_enter_amount_balance_clearing_explainer_title),
                paragraphs = rememberParagraphs(
                    RCommon.string.send_enter_amount_balance_clearing_explainer_first,
                    RCommon.string.send_enter_amount_balance_clearing_explainer_second,
                    RCommon.string.send_enter_amount_balance_clearing_explainer_third
                ),
                onBackClick = {}
            )
        }
    }
}
