package io.paritytech.polkadotapp.feature_wallet_impl.presentation.transactionResult.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.paritytech.polkadotapp.design.components.icon.NovaIcon
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.CheckCircleFilled
import io.paritytech.polkadotapp.design.components.icon.vectors.Clock
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.transactionResult.TransactionSuccessUiState.Finality
import io.paritytech.polkadotapp.common.R as RCommon

private val ICON_SIZE = 16.dp

@Composable
fun PaymentFinalityLabel(
    modifier: Modifier = Modifier,
    finality: Finality,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PolkadotTheme.spacings.tiny)
    ) {
        NovaText(
            modifier = Modifier.weight(1f, fill = false),
            text = stringResource(finality.textRes()),
            style = PolkadotTheme.typography.body.medium,
            textAlign = TextAlign.Center,
            color = PolkadotTheme.colors.fg.secondary
        )

        when (finality) {
            Finality.PENDING -> NovaIcon(
                modifier = Modifier.size(ICON_SIZE),
                imageVector = NovaIcons.Clock,
                tint = PolkadotTheme.colors.fg.secondary
            )

            Finality.CONFIRMED -> NovaIcon(
                modifier = Modifier.size(ICON_SIZE),
                imageVector = NovaIcons.CheckCircleFilled,
                tint = PolkadotTheme.colors.fg.success
            )
        }
    }
}

private fun Finality.textRes(): Int = when (this) {
    Finality.PENDING -> RCommon.string.transaction_success_finality_pending
    Finality.CONFIRMED -> RCommon.string.transaction_success_finality_confirmed
}

@Preview
@Composable
private fun PaymentFinalityPendingLabelPreview() {
    PolkadotTheme {
        PaymentFinalityLabel(finality = Finality.PENDING)
    }
}

@Preview
@Composable
private fun PaymentFinalityConfirmedLabelPreview() {
    PolkadotTheme {
        PaymentFinalityLabel(finality = Finality.CONFIRMED)
    }
}
