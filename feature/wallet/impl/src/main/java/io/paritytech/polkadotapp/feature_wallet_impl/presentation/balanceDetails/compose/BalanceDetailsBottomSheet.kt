@file:OptIn(ExperimentalMaterial3Api::class)

package io.paritytech.polkadotapp.feature_wallet_impl.presentation.balanceDetails.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.paritytech.polkadotapp.common.presentation.loading.dataOrNull
import io.paritytech.polkadotapp.design.components.bottomsheet.NovaBottomSheetSurface
import io.paritytech.polkadotapp.design.components.bottomsheet.NovaModalBottomSheet
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

        BalanceDetailsContent(loadingState.dataOrNull)
    }
}

@Composable
private fun BalanceDetailsContent(state: BalanceDetailsUiState?) {
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

        val rows = balanceRows(state)

        if (rows.isNotEmpty()) {
            PolkadotSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = PolkadotTheme.shapes.large,
                color = PolkadotTheme.colors.bg.surface.nested,
            ) {
                Column(
                    modifier = Modifier.padding(all = PolkadotTheme.spacings.large),
                ) {
                    rows.forEachIndexed { index, row ->
                        if (index > 0) VerticalSpacer { extraLarge }

                        BalanceRow(row)
                    }
                }
            }
        }
    }
}

private class BalanceRowModel(
    val label: String,
    val subLabel: String,
    val amount: String,
)

@Composable
private fun balanceRows(state: BalanceDetailsUiState?): List<BalanceRowModel> {
    // Nothing is known to be empty until the balance arrives, so every row shows as a placeholder rather
    // than appearing one by one as the numbers land.
    val loading = state == null

    // Exposed funds are spendable in principle; whether they are spendable *here* is the chosen strategy's
    // call, and the label has to say which of the two the user is looking at.
    val exposedSpendable = state?.canSpendExposed != false

    return listOfNotNull(
        balanceRowOrNull(
            amount = state?.availablePrivate,
            loading = loading,
            label = RCommon.string.balance_details_available_private,
            subLabel = RCommon.string.balance_details_available_private_description,
        ),
        balanceRowOrNull(
            amount = state?.exposed,
            loading = loading,
            label = if (exposedSpendable) {
                RCommon.string.balance_details_available_exposed
            } else {
                RCommon.string.balance_details_exposed_unavailable
            },
            subLabel = if (exposedSpendable) {
                RCommon.string.balance_details_available_exposed_description
            } else {
                RCommon.string.balance_details_exposed_unavailable_description
            },
        ),
        balanceRowOrNull(
            amount = state?.notAvailable,
            loading = loading,
            label = RCommon.string.balance_details_not_available,
            subLabel = RCommon.string.balance_details_not_available_description,
        ),
    )
}

@Composable
private fun balanceRowOrNull(
    amount: TokenAmountModel?,
    loading: Boolean,
    @StringRes label: Int,
    @StringRes subLabel: Int,
): BalanceRowModel? = when {
    amount == null && !loading -> null

    else -> BalanceRowModel(
        label = stringResource(label),
        subLabel = stringResource(subLabel),
        amount = amount.formattedOrPlaceholder(),
    )
}

@Composable
private fun BalanceRow(row: BalanceRowModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            NovaText(
                text = row.label,
                style = PolkadotTheme.typography.title.small,
                color = PolkadotTheme.colors.fg.primary,
            )

            VerticalSpacer { tiny }

            NovaText(
                text = row.subLabel,
                style = PolkadotTheme.typography.body.medium,
                color = PolkadotTheme.colors.fg.secondary,
            )
        }

        NovaText(
            modifier = Modifier.padding(start = PolkadotTheme.spacings.medium),
            text = row.amount,
            style = PolkadotTheme.typography.title.small,
            color = PolkadotTheme.colors.fg.primary,
        )
    }
}

@Composable
private fun TokenAmountModel?.formattedOrPlaceholder(): String {
    if (this == null) return MISSING_AMOUNT_PLACEHOLDER

    return LocalTokenAmountFormatter.current.formatFiat(this)
}

@Preview
@Composable
private fun BalanceDetailsEveryBucketPreview() {
    BalanceDetailsPreview(
        BalanceDetailsUiState(
            availablePrivate = TokenAmountModel.mock(300),
            exposed = TokenAmountModel.mock(150),
            canSpendExposed = true,
            notAvailable = TokenAmountModel.mock(40),
        )
    )
}

/** Nothing is settling, so that row is absent rather than showing a zero. */
@Preview
@Composable
private fun BalanceDetailsNothingUnavailablePreview() {
    BalanceDetailsPreview(
        BalanceDetailsUiState(
            availablePrivate = TokenAmountModel.mock(300),
            exposed = TokenAmountModel.mock(150),
            canSpendExposed = true,
            notAvailable = null,
        )
    )
}

/** Maximum privacy will not part with what it is holding, so the exposed row says so and stands alone. */
@Preview
@Composable
private fun BalanceDetailsExposedUnavailablePreview() {
    BalanceDetailsPreview(
        BalanceDetailsUiState(
            availablePrivate = null,
            exposed = TokenAmountModel.mock(150),
            canSpendExposed = false,
            notAvailable = null,
        )
    )
}

@Preview
@Composable
private fun BalanceDetailsLoadingPreview() {
    BalanceDetailsPreview(state = null)
}

@Composable
private fun BalanceDetailsPreview(state: BalanceDetailsUiState?) {
    PolkadotTheme {
        CompositionLocalProvider(
            LocalTokenAmountFormatter provides TokenAmountFormatter.mocked
        ) {
            NovaBottomSheetSurface {
                BalanceDetailsContent(state)
            }
        }
    }
}
