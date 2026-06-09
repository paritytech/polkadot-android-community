package io.paritytech.polkadotapp.feature_products_impl.presentation.topUpRequest.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.paritytech.polkadotapp.common.presentation.loading.onLoaded
import io.paritytech.polkadotapp.design.components.bottomsheet.NovaBottomSheetSurface
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonStyle
import io.paritytech.polkadotapp.design.components.button.default.PolkadotTextButton
import io.paritytech.polkadotapp.design.components.spacer.VerticalSpacer
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_products_impl.presentation.topUpRequest.TopUpRequestContract
import io.paritytech.polkadotapp.feature_products_impl.presentation.topUpRequest.TopUpRequestUiState
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.LocalTokenAmountFormatter
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.TokenAmountFormatter
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.RoundPrecision
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.TokenAmountModel
import io.paritytech.polkadotapp.common.R as RCommon

@Composable
fun TopUpRequestScreen(contract: TopUpRequestContract) {
    val state by contract.state.collectAsStateWithLifecycle()

    state.onLoaded { data ->
        TopUpRequestScreenInternal(
            state = data,
            onClaim = contract::onClaimClicked,
        )
    }
}

@Composable
private fun TopUpRequestScreenInternal(
    state: TopUpRequestUiState,
    onClaim: () -> Unit,
) {
    NovaBottomSheetSurface {
        Column(
            modifier = Modifier.padding(
                top = PolkadotTheme.spacings.large,
                bottom = PolkadotTheme.spacings.mediumIncreased,
                start = PolkadotTheme.spacings.mediumIncreased,
                end = PolkadotTheme.spacings.mediumIncreased,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val formatter = LocalTokenAmountFormatter.current
            NovaText(
                text = stringResource(
                    RCommon.string.product_top_up_title,
                    state.productId,
                    formatter.formatTokenAmount(state.amount, precision = RoundPrecision.DEFAULT),
                ),
                style = PolkadotTheme.typography.title.large,
                color = PolkadotTheme.colors.fg.primary,
            )

            if (state.amountMismatch) {
                VerticalSpacer { mediumIncreased }

                NovaText(
                    text = stringResource(RCommon.string.product_top_up_amount_mismatch_warning),
                    style = PolkadotTheme.typography.body.medium,
                    color = PolkadotTheme.colors.fg.warning,
                )
            }

            VerticalSpacer { extraLarge }

            PolkadotTextButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(RCommon.string.product_top_up_claim),
                style = PolkadotButtonStyle.secondary(),
                loading = state.claiming,
                enabled = !state.claiming,
                onClick = onClaim,
            )
        }
    }
}

@Preview
@Composable
private fun TopUpRequestScreenPreview() {
    CompositionLocalProvider(
        LocalTokenAmountFormatter provides TokenAmountFormatter.mocked
    ) {
        PolkadotTheme {
            TopUpRequestScreenInternal(
                state = TopUpRequestUiState(
                    productId = "alice.dot",
                    amount = TokenAmountModel.mock,
                    claiming = false,
                    amountMismatch = false,
                ),
                onClaim = {},
            )
        }
    }
}
