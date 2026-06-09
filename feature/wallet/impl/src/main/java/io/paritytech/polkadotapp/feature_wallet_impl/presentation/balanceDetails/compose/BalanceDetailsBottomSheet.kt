@file:OptIn(ExperimentalMaterial3Api::class)

package io.paritytech.polkadotapp.feature_wallet_impl.presentation.balanceDetails.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.paritytech.polkadotapp.common.presentation.loading.LoadingState
import io.paritytech.polkadotapp.common.presentation.loading.dataOrNull
import io.paritytech.polkadotapp.design.components.bottomsheet.NovaBottomSheetDefaults
import io.paritytech.polkadotapp.design.components.bottomsheet.NovaBottomSheetSurface
import io.paritytech.polkadotapp.design.components.bottomsheet.NovaModalBottomSheet
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonStyle
import io.paritytech.polkadotapp.design.components.button.icon.PolkadotIconButton
import io.paritytech.polkadotapp.design.components.button.icon.PolkadotIconButtonSize
import io.paritytech.polkadotapp.design.components.icon.NovaIcon
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.ArrowLeft
import io.paritytech.polkadotapp.design.components.icon.vectors.Info
import io.paritytech.polkadotapp.design.components.spacer.HorizontalSpacer
import io.paritytech.polkadotapp.design.components.spacer.VerticalSpacer
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.LocalTokenAmountFormatter
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.TokenAmountFormatter
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.formatFiat
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.TokenAmountModel
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.balanceDetails.BalanceDetailsUiState
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.balanceDetails.BalanceDetailsViewModel
import io.paritytech.polkadotapp.common.R as RCommon

private const val MISSING_AMOUNT_PLACEHOLDER = "—"

@Composable
fun BalanceDetailsBottomSheet(
    isVisible: Boolean,
    onDismissRequest: () -> Unit,
) {
    NovaModalBottomSheet(
        isVisible = isVisible,
        onDismissRequest = onDismissRequest,
    ) {
        val viewModel = hiltViewModel<BalanceDetailsViewModel>()
        val loadingState by viewModel.state.collectAsStateWithLifecycle()

        BalanceDetailsBottomSheetContent(
            loadingState = loadingState,
            isVisible = isVisible,
        )
    }
}

private sealed interface Page {
    data object Root : Page
    data object Info : Page
}

private enum class InfoContent { AvailableNow, AvailableSoon }

@Composable
private fun BalanceDetailsBottomSheetContent(
    loadingState: LoadingState<BalanceDetailsUiState>,
    isVisible: Boolean,
) {
    var page: Page by remember { mutableStateOf(Page.Root) }
    var infoContent: InfoContent by remember { mutableStateOf(InfoContent.AvailableNow) }
    LaunchedEffect(isVisible) { if (isVisible) page = Page.Root }

    AnimatedContent(
        targetState = page,
        transitionSpec = { NovaBottomSheetDefaults.PAGE_TRANSITION_SPEC }
    ) { current ->
        when (current) {
            Page.Root -> RootPage(
                state = loadingState.dataOrNull,
                onAvailableNowInfo = {
                    infoContent = InfoContent.AvailableNow
                    page = Page.Info
                },
                onAvailableSoonInfo = {
                    infoContent = InfoContent.AvailableSoon
                    page = Page.Info
                },
            )

            Page.Info -> when (infoContent) {
                InfoContent.AvailableNow -> AvailableNowInfoPage(onBack = { page = Page.Root })
                InfoContent.AvailableSoon -> AvailableSoonInfoPage(onBack = { page = Page.Root })
            }
        }
    }
}

@Composable
private fun RootPage(
    state: BalanceDetailsUiState?,
    onAvailableNowInfo: () -> Unit,
    onAvailableSoonInfo: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(all = PolkadotTheme.spacings.large),
    ) {
        NovaText(
            text = stringResource(RCommon.string.balance_details_title),
            style = PolkadotTheme.typography.title.large,
            color = PolkadotTheme.colors.fg.primary,
        )

        VerticalSpacer { tiny }

        NovaText(
            text = stringResource(RCommon.string.balance_details_subtitle),
            style = PolkadotTheme.typography.body.medium,
            color = PolkadotTheme.colors.fg.secondary,
        )

        VerticalSpacer { mediumIncreased }

        PolkadotSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = PolkadotTheme.shapes.large,
            color = PolkadotTheme.colors.bg.surface.nested,
        ) {
            Column(
                modifier = Modifier.padding(all = PolkadotTheme.spacings.large),
            ) {
                BalanceRow(
                    label = stringResource(RCommon.string.balance_details_total),
                    subLabel = stringResource(RCommon.string.balance_details_total_description),
                    amount = state?.totalBalance.formattedOrPlaceholder(),
                )

                VerticalSpacer { extraLarge }

                BalanceRow(
                    label = stringResource(RCommon.string.balance_details_available_now),
                    subLabel = stringResource(RCommon.string.balance_details_available_now_description),
                    amount = state?.availableNow.formattedOrPlaceholder(),
                    onInfoClick = onAvailableNowInfo,
                )

                VerticalSpacer { small }

                SubBalanceRow(
                    label = stringResource(RCommon.string.balance_details_available_now_secured),
                    amount = state?.availableNowSecured.formattedOrPlaceholder(),
                )

                VerticalSpacer { small }

                SubBalanceRow(
                    label = stringResource(RCommon.string.balance_details_available_now_low_privacy),
                    amount = state?.availableNowLowPrivacy.formattedOrPlaceholder(),
                )

                VerticalSpacer { extraLarge }

                BalanceRow(
                    label = stringResource(RCommon.string.balance_details_available_soon),
                    subLabel = stringResource(RCommon.string.balance_details_available_soon_description),
                    amount = state?.availableSoon.formattedOrPlaceholder(),
                    onInfoClick = onAvailableSoonInfo,
                )
            }
        }
    }
}

@Composable
private fun BalanceRow(
    label: String,
    subLabel: String,
    amount: String,
    onInfoClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NovaText(
                    text = label,
                    style = PolkadotTheme.typography.title.small,
                    color = PolkadotTheme.colors.fg.primary,
                )

                if (onInfoClick != null) {
                    HorizontalSpacer { tiny }

                    NovaIcon(
                        modifier = Modifier
                            .size(16.dp)
                            .clickable(onClick = onInfoClick),
                        imageVector = NovaIcons.Info,
                        tint = PolkadotTheme.colors.fg.tertiary,
                    )
                }
            }

            VerticalSpacer { tiny }

            NovaText(
                text = subLabel,
                style = PolkadotTheme.typography.body.small,
                color = PolkadotTheme.colors.fg.secondary,
            )
        }

        NovaText(
            text = amount,
            style = PolkadotTheme.typography.body.large,
            color = PolkadotTheme.colors.fg.primary,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun SubBalanceRow(
    label: String,
    amount: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        NovaText(
            text = label,
            style = PolkadotTheme.typography.title.small,
            color = PolkadotTheme.colors.fg.secondary,
        )

        NovaText(
            text = amount,
            style = PolkadotTheme.typography.title.small,
            color = PolkadotTheme.colors.fg.secondary,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun AvailableNowInfoPage(onBack: () -> Unit) {
    InfoPageScaffold(onBack = onBack) {
        NovaText(
            text = stringResource(RCommon.string.balance_details_available_now_info_title),
            style = PolkadotTheme.typography.headline.small,
            color = PolkadotTheme.colors.fg.primary,
        )

        VerticalSpacer { extraMedium }

        NovaText(
            text = stringResource(RCommon.string.balance_details_available_now_info_body),
            style = PolkadotTheme.typography.body.large,
            color = PolkadotTheme.colors.fg.secondary,
        )

        VerticalSpacer { mediumIncreased }

        NovaText(
            text = stringResource(RCommon.string.balance_details_available_now_why_subtitle),
            style = PolkadotTheme.typography.headline.small,
            color = PolkadotTheme.colors.fg.primary,
        )

        VerticalSpacer { extraMedium }

        NovaText(
            text = stringResource(RCommon.string.balance_details_available_now_why_body),
            style = PolkadotTheme.typography.body.large,
            color = PolkadotTheme.colors.fg.secondary,
        )
    }
}

@Composable
private fun AvailableSoonInfoPage(onBack: () -> Unit) {
    InfoPageScaffold(onBack = onBack) {
        NovaText(
            text = stringResource(RCommon.string.balance_details_available_soon_info_title),
            style = PolkadotTheme.typography.headline.small,
            color = PolkadotTheme.colors.fg.primary,
        )

        VerticalSpacer { extraMedium }

        NovaText(
            text = stringResource(RCommon.string.balance_details_available_soon_info_body),
            style = PolkadotTheme.typography.body.large,
            color = PolkadotTheme.colors.fg.secondary,
        )
    }
}

@Composable
private fun InfoPageScaffold(
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(all = PolkadotTheme.spacings.large),
    ) {
        PolkadotIconButton(
            modifier = Modifier.align(Alignment.Start),
            icon = NovaIcons.ArrowLeft,
            onClick = onBack,
            size = PolkadotIconButtonSize.small(),
            style = PolkadotButtonStyle.ghost()
        )

        VerticalSpacer { extraLarge }

        content()
    }
}

@Composable
private fun TokenAmountModel?.formattedOrPlaceholder(): String {
    return if (this != null) {
        LocalTokenAmountFormatter.current.formatFiat(this)
    } else {
        MISSING_AMOUNT_PLACEHOLDER
    }
}

@Preview
@Composable
private fun RootPagePreview() {
    PolkadotTheme {
        CompositionLocalProvider(LocalTokenAmountFormatter provides TokenAmountFormatter.mocked) {
            NovaBottomSheetSurface {
                RootPage(
                    state = BalanceDetailsUiState(
                        totalBalance = TokenAmountModel.mock,
                        availableNow = TokenAmountModel.mock,
                        availableNowSecured = TokenAmountModel.mock,
                        availableNowLowPrivacy = TokenAmountModel.mock,
                        availableSoon = TokenAmountModel.mock,
                    ),
                    onAvailableNowInfo = {},
                    onAvailableSoonInfo = {},
                )
            }
        }
    }
}

@Preview
@Composable
private fun AvailableNowInfoPreview() {
    PolkadotTheme {
        NovaBottomSheetSurface {
            AvailableNowInfoPage(onBack = {})
        }
    }
}

@Preview
@Composable
private fun AvailableSoonInfoPreview() {
    PolkadotTheme {
        NovaBottomSheetSurface {
            AvailableSoonInfoPage(onBack = {})
        }
    }
}
