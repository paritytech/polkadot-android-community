package io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.paritytech.polkadotapp.common.R
import io.paritytech.polkadotapp.design.components.icon.NovaIcon
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.Info
import io.paritytech.polkadotapp.design.components.spacer.HorizontalSpacer
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme

@Composable
internal fun EnterAmountBalance(
    amount: String,
    onInfoClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        NovaText(
            text = amount,
            style = PolkadotTheme.typography.body.large,
            color = PolkadotTheme.colors.fg.primary
        )

        HorizontalSpacer { tiny }

        NovaText(
            text = stringResource(R.string.send_enter_amount_your_balance_label),
            style = PolkadotTheme.typography.body.large,
            color = PolkadotTheme.colors.fg.secondary,
        )

        HorizontalSpacer { tiny }

        NovaIcon(
            modifier = Modifier
                .size(20.dp)
                .clickable(onClick = onInfoClick),
            imageVector = NovaIcons.Info,
            tint = PolkadotTheme.colors.fg.secondary,
        )
    }
}

@Preview
@Composable
fun EnterAmountBalancePreview() {
    PolkadotTheme {
        EnterAmountBalance(
            amount = "$300",
            onInfoClick = {}
        )
    }
}
