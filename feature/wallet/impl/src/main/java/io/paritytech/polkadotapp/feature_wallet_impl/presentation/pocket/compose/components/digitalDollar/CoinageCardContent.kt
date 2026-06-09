@file:OptIn(ExperimentalMaterial3Api::class)

package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.components.digitalDollar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.paritytech.polkadotapp.design.components.bottomsheet.NovaModalBottomSheet
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonStyle
import io.paritytech.polkadotapp.design.components.button.default.PolkadotTextButton
import io.paritytech.polkadotapp.design.components.icon.NovaIcon
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.Add
import io.paritytech.polkadotapp.design.components.progress.NovaCircularProgressIndicator
import io.paritytech.polkadotapp.design.components.spacer.VerticalSpacer
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.models.CoinageUiState
import io.paritytech.polkadotapp.common.R as RCommon

private val SEND_BUTTON_SIZE = 52.dp

@Composable
fun CoinageCardContent(
    state: CoinageUiState,
    onFundClick: () -> Unit,
    onAutoFundClick: () -> Unit,
    makeAllVouchersReady: () -> Unit,
    onShareLogsClick: () -> Unit,
    onForceRecycleClick: (Coin) -> Unit
) {
    Column {
        var details by remember { mutableStateOf<CoinageDetails?>(null) }

        FundActions(
            modifier = Modifier.fillMaxWidth(),
            autoFundAvailable = state.autoFundAvailable,
            fundInProgress = state.fundInProgress,
            actionsEnabled = state.actionsEnabled,
            onFundClick = onFundClick,
            onAutoFundClick = onAutoFundClick
        )

        if (state.coinageWidgetsEnabled) {
            VerticalSpacer { mediumIncreased }

            CoinageStateCard(
                state = state.tokensState,
                onCoinsClick = { details = CoinageDetails.COINS },
                onVouchersClick = { details = CoinageDetails.VOUCHERS },
                makeAllVouchersReady = makeAllVouchersReady,
                onShareLogsClick = onShareLogsClick
            )

            NovaModalBottomSheet(
                isVisible = details == CoinageDetails.COINS,
                onDismissRequest = { details = null }
            ) {
                CoinsListSheetContent(
                    coins = state.tokensState.coinList,
                    onForceRecycleClick = onForceRecycleClick
                )
            }

            NovaModalBottomSheet(
                isVisible = details == CoinageDetails.VOUCHERS,
                onDismissRequest = { details = null }
            ) {
                VouchersListSheetContent(vouchers = state.tokensState.voucherList)
            }
        }
    }
}

@Composable
private fun FundActions(
    modifier: Modifier = Modifier,
    autoFundAvailable: Boolean,
    fundInProgress: Boolean,
    actionsEnabled: Boolean,
    onFundClick: () -> Unit,
    onAutoFundClick: () -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PolkadotTheme.spacings.extraMedium)
    ) {
        PolkadotTextButton(
            modifier = Modifier.weight(1f),
            text = stringResource(RCommon.string.asset_details_fund_digital_dollar),
            style = PolkadotButtonStyle.secondary(),
            enabled = actionsEnabled,
            onClick = onFundClick
        )

        if (autoFundAvailable) {
            IconButton(
                modifier = Modifier.size(SEND_BUTTON_SIZE),
                shape = PolkadotTheme.shapes.full,
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0x0FFFFFFF)),
                enabled = actionsEnabled,
                onClick = onAutoFundClick
            ) {
                if (fundInProgress) {
                    NovaCircularProgressIndicator(modifier = Modifier.padding(12.dp))
                } else {
                    NovaIcon(imageVector = NovaIcons.Add, tint = PolkadotTheme.colors.fg.primary)
                }
            }
        }
    }
}
