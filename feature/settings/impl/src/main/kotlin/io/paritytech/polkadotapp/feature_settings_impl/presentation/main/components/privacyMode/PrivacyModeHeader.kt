package io.paritytech.polkadotapp.feature_settings_impl.presentation.main.components.privacyMode

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.paritytech.polkadotapp.common.presentation.compose.withCurrencyTickerStyle
import io.paritytech.polkadotapp.common.presentation.paymentAsset.LocalPaymentAssetBrand
import io.paritytech.polkadotapp.design.components.icon.NovaIcon
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.Info
import io.paritytech.polkadotapp.design.components.icon.vectors.ShieldOutlined
import io.paritytech.polkadotapp.design.components.spacer.HorizontalSpacer
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.common.R as RCommon

@Composable
internal fun PrivacyModeHeader(onInfoClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        NovaIcon(
            modifier = Modifier.size(HEADER_ICON_SIZE),
            imageVector = NovaIcons.ShieldOutlined,
            tint = PolkadotTheme.colors.fg.secondary
        )

        HorizontalSpacer { small }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = PolkadotTheme.spacings.extraTiny)
        ) {
            NovaText(
                text = stringResource(RCommon.string.payment_privacy_mode_title, LocalPaymentAssetBrand.current.symbol)
                    .withCurrencyTickerStyle(PolkadotTheme.typography.body.large),
                style = PolkadotTheme.typography.body.large,
                color = PolkadotTheme.colors.fg.primary
            )

            NovaText(
                text = stringResource(RCommon.string.payment_privacy_mode_subtitle),
                style = PolkadotTheme.typography.paragraph.medium,
                color = PolkadotTheme.colors.fg.secondary
            )
        }

        HorizontalSpacer { extraMedium }

        NovaIcon(
            modifier = Modifier
                .size(INFO_ICON_SIZE)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onInfoClick),
            imageVector = NovaIcons.Info,
            contentDescription = stringResource(RCommon.string.payment_privacy_mode_coin_quality_title),
            tint = PolkadotTheme.colors.fg.secondary
        )
    }
}

private val HEADER_ICON_SIZE = 24.dp
private val INFO_ICON_SIZE = 20.dp
