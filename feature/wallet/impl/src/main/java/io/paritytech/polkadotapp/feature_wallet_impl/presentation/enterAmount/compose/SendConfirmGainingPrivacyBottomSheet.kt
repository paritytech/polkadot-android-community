@file:OptIn(ExperimentalMaterial3Api::class)

package io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.paritytech.polkadotapp.common.utils.isZero
import io.paritytech.polkadotapp.design.components.bottomsheet.NovaBottomSheetDefaults
import io.paritytech.polkadotapp.design.components.bottomsheet.NovaBottomSheetSurface
import io.paritytech.polkadotapp.design.components.bottomsheet.NovaModalBottomSheet
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonStyle
import io.paritytech.polkadotapp.design.components.button.default.PolkadotTextButton
import io.paritytech.polkadotapp.design.components.button.icon.PolkadotIconButton
import io.paritytech.polkadotapp.design.components.button.icon.PolkadotIconButtonSize
import io.paritytech.polkadotapp.design.components.icon.NovaIcon
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.ArrowLeft
import io.paritytech.polkadotapp.design.components.icon.vectors.ArrowRight
import io.paritytech.polkadotapp.design.components.icon.vectors.Info
import io.paritytech.polkadotapp.design.components.spacer.HorizontalSpacer
import io.paritytech.polkadotapp.design.components.spacer.VerticalSpacer
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.LocalTokenAmountFormatter
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.TokenAmountFormatter
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.formatFiat
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.TokenAmountModel
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.TokenSymbolAppearance
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount.domain.ConfirmGainingPrivacySpendUserAction
import java.math.BigDecimal
import io.paritytech.polkadotapp.common.R as RCommon

@Composable
fun SendConfirmGainingPrivacyBottomSheet(
    isVisible: Boolean,
    action: ConfirmGainingPrivacySpendUserAction,
    onSendAnyway: () -> Unit,
    onDismiss: () -> Unit,
) {
    NovaModalBottomSheet(
        isVisible = isVisible,
        onDismissRequest = onDismiss,
    ) {
        SendConfirmGainingPrivacyContent(
            action = action,
            isVisible = isVisible,
            onSendAnyway = onSendAnyway,
            onCancel = onDismiss,
        )
    }
}

private sealed interface Page {
    data object Details : Page
    data object WhyExplain : Page
}

@Composable
private fun SendConfirmGainingPrivacyContent(
    action: ConfirmGainingPrivacySpendUserAction,
    isVisible: Boolean,
    onSendAnyway: () -> Unit,
    onCancel: () -> Unit,
) {
    var page: Page by remember { mutableStateOf(Page.Details) }
    LaunchedEffect(isVisible) { if (isVisible) page = Page.Details }

    AnimatedContent(
        targetState = page,
        transitionSpec = { NovaBottomSheetDefaults.PAGE_TRANSITION_SPEC }
    ) { currentPage ->
        when (currentPage) {
            Page.Details -> DetailsPage(
                action = action,
                onWhyClick = { page = Page.WhyExplain },
                onSendAnyway = onSendAnyway,
                onCancel = onCancel,
            )

            Page.WhyExplain -> WhyExplainPage(
                onBack = { page = Page.Details },
            )
        }
    }
}

@Composable
private fun DetailsPage(
    action: ConfirmGainingPrivacySpendUserAction,
    onWhyClick: () -> Unit,
    onSendAnyway: () -> Unit,
    onCancel: () -> Unit,
) {
    val formatter = LocalTokenAmountFormatter.current

    val hasSpendable = !action.spendable.amount.isZero()
    val totalAmountText = formatter.formatFiat(action.totalTransfer)
    val spendableAmountText = formatter.formatFiat(action.spendable)
    val gainingPrivacyAmountText = formatter.formatFiat(action.gainingPrivacy)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(all = PolkadotTheme.spacings.mediumIncreased),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VerticalSpacer { small }

        NovaIcon(
            modifier = Modifier.size(50.dp),
            imageVector = NovaIcons.Info,
            tint = PolkadotTheme.colors.fg.warning
        )

        VerticalSpacer { mediumIncreased }

        NovaText(
            text = stringResource(RCommon.string.send_gaining_privacy_title),
            style = PolkadotTheme.typography.headline.small,
            color = PolkadotTheme.colors.fg.primary,
            textAlign = TextAlign.Center,
        )

        VerticalSpacer { small }

        if (hasSpendable) {
            NovaText(
                text = stringResource(RCommon.string.send_gaining_privacy_spendable, spendableAmountText),
                style = PolkadotTheme.typography.body.large,
                color = PolkadotTheme.colors.fg.secondary,
                textAlign = TextAlign.Center,
            )

            VerticalSpacer { tiny }
        }

        NovaText(
            text = stringResource(
                RCommon.string.send_gaining_privacy_full_amount,
                totalAmountText,
                gainingPrivacyAmountText,
            ),
            style = PolkadotTheme.typography.body.large,
            color = PolkadotTheme.colors.fg.secondary,
            textAlign = TextAlign.Center,
        )

        VerticalSpacer { mediumIncreased }

        WhyLink(
            text = stringResource(RCommon.string.send_gaining_privacy_why_link, gainingPrivacyAmountText),
            onClick = onWhyClick,
        )

        VerticalSpacer { extraLarge }

        PolkadotTextButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(RCommon.string.send_gaining_privacy_send_anyway_button, totalAmountText),
            style = PolkadotButtonStyle.tertiary(),
            onClick = onSendAnyway,
        )

        VerticalSpacer { small }

        PolkadotTextButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(RCommon.string.common_cancel),
            style = PolkadotButtonStyle.ghost(),
            onClick = onCancel,
        )
    }
}

@Composable
private fun WhyLink(
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(PolkadotTheme.spacings.tiny),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        NovaText(
            text = text,
            style = PolkadotTheme.typography.body.large,
            color = PolkadotTheme.colors.fg.secondary,
        )
        HorizontalSpacer { tiny }
        NovaIcon(
            modifier = Modifier.size(16.dp),
            imageVector = NovaIcons.ArrowRight,
            tint = PolkadotTheme.colors.fg.secondary,
        )
    }
}

@Composable
private fun WhyExplainPage(
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(all = PolkadotTheme.spacings.mediumIncreased),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PolkadotIconButton(
            modifier = Modifier.align(Alignment.Start),
            icon = NovaIcons.ArrowLeft,
            onClick = onBack,
            size = PolkadotIconButtonSize.small(),
            style = PolkadotButtonStyle.ghost()
        )

        VerticalSpacer { mediumIncreased }

        NovaText(
            text = stringResource(RCommon.string.send_gaining_privacy_why_title),
            style = PolkadotTheme.typography.headline.small,
            color = PolkadotTheme.colors.fg.primary,
            textAlign = TextAlign.Center,
        )

        VerticalSpacer { small }

        NovaText(
            text = stringResource(RCommon.string.send_gaining_privacy_why_body),
            style = PolkadotTheme.typography.body.large,
            color = PolkadotTheme.colors.fg.secondary,
            textAlign = TextAlign.Center,
        )

        VerticalSpacer { extraLarge }

        PolkadotTextButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(RCommon.string.common_close),
            style = PolkadotButtonStyle.tertiary(),
            onClick = onBack,
        )
    }
}

@Preview
@Composable
private fun SendConfirmGainingPrivacyDetailsPreview() {
    fun createTokenAmountModel(amount: BigDecimal): TokenAmountModel {
        return object : TokenAmountModel {
            override val amount: BigDecimal = amount
            override val appearance: TokenSymbolAppearance = TokenSymbolAppearance.DigitalDollar
        }
    }
    PolkadotTheme {
        CompositionLocalProvider(
            LocalTokenAmountFormatter provides TokenAmountFormatter.mocked
        ) {
            NovaBottomSheetSurface {
                DetailsPage(
                    action = ConfirmGainingPrivacySpendUserAction(
                        totalTransfer = createTokenAmountModel(100.toBigDecimal()),
                        spendable = createTokenAmountModel(60.toBigDecimal()),
                        gainingPrivacy = createTokenAmountModel(40.toBigDecimal()),
                    ),
                    onWhyClick = {},
                    onSendAnyway = {},
                    onCancel = {},
                )
            }
        }
    }
}

@Preview
@Composable
private fun SendConfirmGainingPrivacyWhyExplainPreview() {
    PolkadotTheme {
        NovaBottomSheetSurface {
            WhyExplainPage(onBack = {})
        }
    }
}
