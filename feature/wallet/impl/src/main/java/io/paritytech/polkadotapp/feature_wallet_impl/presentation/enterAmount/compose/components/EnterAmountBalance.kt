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
import io.paritytech.polkadotapp.design.components.icon.NovaIcon
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.Info
import io.paritytech.polkadotapp.design.components.icon.vectors.VisibilityOnFilled
import io.paritytech.polkadotapp.design.components.spacer.HorizontalSpacer
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.common.R as RCommon

/**
 * [gainingPrivacy] is shown apart from the balance rather than added to it: it is spendable, but only at the
 * cost of the privacy it has earned, so it should not read as money simply sitting there. The open eye is
 * what being seen looks like elsewhere in the app, so it names that cost; a crossed-out eye would read as the
 * opposite, that the amount is hidden.
 */
@Composable
internal fun EnterAmountBalance(
    amount: String,
    gainingPrivacy: String?,
    onInfoClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        NovaText(
            text = stringResource(RCommon.string.send_enter_amount_max_balance_prefix),
            style = PolkadotTheme.typography.body.large,
            color = PolkadotTheme.colors.fg.secondary
        )

        HorizontalSpacer { tiny }

        NovaText(
            text = amount,
            style = PolkadotTheme.typography.body.large,
            color = PolkadotTheme.colors.fg.primary
        )

        if (gainingPrivacy != null) {
            HorizontalSpacer { extraTiny }

            NovaText(
                text = "+",
                style = PolkadotTheme.typography.body.large,
                color = PolkadotTheme.colors.fg.tertiary,
            )

            HorizontalSpacer { extraTiny }

            NovaText(
                text = gainingPrivacy,
                style = PolkadotTheme.typography.body.large,
                color = PolkadotTheme.colors.fg.tertiary,
            )

            HorizontalSpacer { extraTiny }

            NovaIcon(
                modifier = Modifier.size(16.dp),
                imageVector = NovaIcons.VisibilityOnFilled,
                tint = PolkadotTheme.colors.fg.tertiary,
            )
        }

        HorizontalSpacer { small }

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
private fun EnterAmountBalancePreview() {
    PolkadotTheme {
        EnterAmountBalance(
            amount = "$300",
            gainingPrivacy = "$150",
            onInfoClick = {}
        )
    }
}

@Preview
@Composable
private fun EnterAmountBalanceNothingExposedPreview() {
    PolkadotTheme {
        EnterAmountBalance(
            amount = "$300",
            gainingPrivacy = null,
            onInfoClick = {}
        )
    }
}
