package io.paritytech.polkadotapp.feature_coinage_api.presentation.notice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import io.paritytech.polkadotapp.common.presentation.notification.AppNoticeItem
import io.paritytech.polkadotapp.common.presentation.paymentAsset.LocalPaymentAssetBrand
import io.paritytech.polkadotapp.common.presentation.paymentAsset.PaymentAssetBrand
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonStyle
import io.paritytech.polkadotapp.design.components.button.icon.PolkadotIconButton
import io.paritytech.polkadotapp.design.components.button.icon.PolkadotIconButtonSize
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.Close
import io.paritytech.polkadotapp.design.components.icon.vectors.Info
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.common.R as RCommon

@Composable
fun CoinageSetupNoticeHost(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    onDismiss: () -> Unit,
) {
    Box(modifier = modifier) {
        AnimatedVisibility(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(
                    horizontal = PolkadotTheme.spacings.mediumIncreased,
                    vertical = PolkadotTheme.spacings.large,
                ),
            visible = isVisible,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        ) {
            CoinageSetupNotice(onDismiss = onDismiss)
        }
    }
}

@Composable
private fun CoinageSetupNotice(onDismiss: () -> Unit) {
    val dismissLabel = stringResource(RCommon.string.coinage_setup_notice_dismiss)

    AppNoticeItem(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures { } },
        icon = NovaIcons.Info,
        iconTint = PolkadotTheme.colors.fg.secondary,
        background = PolkadotTheme.colors.bg.action.tertiary,
        message = stringResource(RCommon.string.coinage_setup_notice_message),
        trailing = {
            PolkadotIconButton(
                modifier = Modifier.semantics { contentDescription = dismissLabel },
                icon = NovaIcons.Close,
                onClick = onDismiss,
                style = PolkadotButtonStyle.ghost(),
                size = PolkadotIconButtonSize.small(),
            )
        },
    )
}

@Preview
@Composable
private fun CoinageSetupNoticePreview() = CompositionLocalProvider(
    LocalPaymentAssetBrand provides PaymentAssetBrand.mocked
) {
    PolkadotTheme {
        CoinageSetupNotice(onDismiss = {})
    }
}
