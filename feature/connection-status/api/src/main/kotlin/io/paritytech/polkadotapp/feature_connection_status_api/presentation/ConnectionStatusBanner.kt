package io.paritytech.polkadotapp.feature_connection_status_api.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ConnectionStatusBannerModel
import io.paritytech.polkadotapp.common.R as RCommon

@Composable
fun ConnectionStatusBanner(
    modifier: Modifier = Modifier,
    model: ConnectionStatusBannerModel,
) {
    when (model) {
        ConnectionStatusBannerModel.WaitingForNetwork -> BannerContent(
            modifier = modifier,
            background = PolkadotTheme.colors.bg.status.error,
            text = stringResource(RCommon.string.connection_status_waiting_for_network),
        )
        is ConnectionStatusBannerModel.Connecting -> BannerContent(
            modifier = modifier,
            background = PolkadotTheme.colors.bg.status.warning,
            text = stringResource(
                RCommon.string.connection_status_connecting,
                model.connectedChains,
                model.totalChains,
            ),
        )
        ConnectionStatusBannerModel.None -> Unit
    }
}

@Composable
private fun BannerContent(
    modifier: Modifier,
    background: Color,
    text: String,
) {
    PolkadotSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = background,
        contentAlignment = Alignment.Center,
    ) {
        NovaText(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = PolkadotTheme.spacings.extraMedium)
                .padding(bottom = PolkadotTheme.spacings.small),
            text = text,
            style = PolkadotTheme.typography.title.small,
            color = PolkadotTheme.colors.fg.primary,
        )
    }
}

@Preview
@Composable
private fun ConnectionStatusBannerWaitingForNetworkPreview() {
    PolkadotTheme {
        ConnectionStatusBanner(model = ConnectionStatusBannerModel.WaitingForNetwork)
    }
}

@Preview
@Composable
private fun ConnectionStatusBannerConnectingPreview() {
    PolkadotTheme {
        ConnectionStatusBanner(
            model = ConnectionStatusBannerModel.Connecting(connectedChains = 2, totalChains = 4),
        )
    }
}
