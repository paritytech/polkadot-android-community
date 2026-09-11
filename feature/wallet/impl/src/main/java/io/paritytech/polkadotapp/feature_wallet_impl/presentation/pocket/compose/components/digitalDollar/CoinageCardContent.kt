package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.components.digitalDollar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonStyle
import io.paritytech.polkadotapp.design.components.button.default.PolkadotTextButton
import io.paritytech.polkadotapp.design.components.spacer.VerticalSpacer
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.models.CoinageUiState
import io.paritytech.polkadotapp.common.R as RCommon

@Composable
fun CoinageCardContent(
    state: CoinageUiState,
    onAutoFundClick: () -> Unit,
    onDetailsToggled: () -> Unit,
    onKeyToggled: () -> Unit,
    onShareLogsClick: () -> Unit
) {
    Column {
        if (state.autoFundAvailable) {
            FaucetTopUpButton(
                modifier = Modifier.fillMaxWidth(),
                fundInProgress = state.fundInProgress,
                actionsEnabled = state.actionsEnabled,
                onClick = onAutoFundClick
            )

            VerticalSpacer { mediumIncreased }
        }

        if (state.coinageWidgetsEnabled) {
            CoinageStateCard(
                modifier = Modifier.fillMaxWidth(),
                state = state.tokensState,
                detailsVisible = state.detailsVisible,
                keyVisible = state.keyVisible,
                onDetailsToggled = onDetailsToggled,
                onKeyToggled = onKeyToggled,
                shareLogsEnabled = state.shareLogsEnabled,
                onShareLogsClick = onShareLogsClick
            )
        }
    }
}

@Composable
private fun FaucetTopUpButton(
    modifier: Modifier = Modifier,
    fundInProgress: Boolean,
    actionsEnabled: Boolean,
    onClick: () -> Unit
) {
    PolkadotTextButton(
        modifier = modifier,
        text = stringResource(RCommon.string.pocket_digital_dollar_faucet_top_up),
        style = PolkadotButtonStyle.secondary(),
        enabled = actionsEnabled,
        loading = fundInProgress,
        onClick = onClick
    )
}
