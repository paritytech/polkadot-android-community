package io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.paritytech.polkadotapp.common.presentation.loading.LoadingState
import io.paritytech.polkadotapp.common.presentation.validation.compose.rememberValidationActionHandle
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonShape
import io.paritytech.polkadotapp.design.components.button.default.PolkadotTextButton
import io.paritytech.polkadotapp.design.components.progress.LoadingScreenState
import io.paritytech.polkadotapp.design.components.spacer.VerticalSpacer
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.configs.colors.AvatarColorScheme
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_account_api.presentation.address.model.ExtractedAddress
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.LocalTokenAmountFormatter
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.TokenAmountFormatter
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.RoundPrecision
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.TokenAmountModel
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.balanceDetails.compose.BalanceDetailsBottomSheet
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount.SendEnterAmountContract
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount.SendEnterAmountUiState
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount.SendPlanDebugInfo
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount.compose.components.EnterAmountBalance
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount.compose.components.EnterAmountInput
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount.compose.components.EnterAmountRecipient
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount.compose.components.EnterAmountToolbar
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount.domain.ConfirmDegradedVouchersDecision
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount.domain.ConfirmDegradedVouchersUserAction
import io.paritytech.polkadotapp.common.R as RCommon

@Composable
internal fun SendEnterAmountScreen(contract: SendEnterAmountContract) {
    val state = contract.state.collectAsStateWithLifecycle().value
    var isBalanceDetailsVisible by remember { mutableStateOf(false) }

    PolkadotSurface {
        when (state) {
            is LoadingState.Loaded -> SendEnterAmountScreenInternal(
                state = state.data,
                onAmountChange = contract::onNewInput,
                onConfirmClick = contract::onConfirmClick,
                onBackClick = contract::onBackClick,
                onInfoClick = { isBalanceDetailsVisible = true }
            )

            else -> LoadingScreenState()
        }
    }

    DegradedConfirmationHost(contract)

    BalanceDetailsBottomSheet(
        isVisible = isBalanceDetailsVisible,
        onDismissRequest = { isBalanceDetailsVisible = false },
    )
}

@Composable
private fun DegradedConfirmationHost(contract: SendEnterAmountContract) {
    val handle = contract.sendValidationMixin
        .rememberValidationActionHandle<ConfirmDegradedVouchersUserAction, ConfirmDegradedVouchersDecision>()

    val action = handle.payload

    if (action != null) {
        SendConfirmDegradedStateBottomSheet(
            isVisible = handle.isVisible,
            action = action,
            onSendPrivatelyOnly = { handle.respond(ConfirmDegradedVouchersDecision.SendPrivatelyOnly) },
            onSendWithDegraded = { handle.respond(ConfirmDegradedVouchersDecision.SendWithDegraded) },
            onDismiss = { handle.respond(ConfirmDegradedVouchersDecision.Cancel) },
        )
    }
}

@Composable
private fun SendEnterAmountScreenInternal(
    state: SendEnterAmountUiState,
    onAmountChange: (String) -> Unit,
    onConfirmClick: () -> Unit,
    onInfoClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    val formatter = LocalTokenAmountFormatter.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val symbol = remember(state.available) {
        formatter.formatToSymbol(state.available)
    }
    val amount = remember(state.available) {
        formatter.formatTokenAmount(state.available, RoundPrecision.FIAT, withSymbol = false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
    ) {
        EnterAmountToolbar(onBackClick)

        VerticalSpacer { mediumIncreased }

        if (state.recipient != null) {
            EnterAmountRecipient(
                address = state.recipient,
                type = state.recipientType,
                avatarColor = state.recipientAvatarColor
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            EnterAmountBalance(amount, onInfoClick)

            VerticalSpacer { small }

            EnterAmountInput(
                modifier = Modifier.padding(
                    horizontal = PolkadotTheme.spacings.mediumIncreased
                ),
                input = state.input,
                symbol = symbol,
                showError = state.showBalanceError,
                enabled = !state.isSendInProgress && !state.isAmountLocked,
                focusRequester = focusRequester,
                onInputChange = onAmountChange
            )
        }

        state.debugPlanInfo?.let { DebugPlanInfo(it) }

        PolkadotTextButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PolkadotTheme.spacings.large),
            text = stringResource(RCommon.string.common_send),
            onClick = onConfirmClick,
            enabled = state.isSendEnabled,
            loading = state.isSendInProgress,
            shape = PolkadotButtonShape.pill
        )
    }
}

@Composable
private fun DebugPlanInfo(info: SendPlanDebugInfo) {
    val label = when (info) {
        is SendPlanDebugInfo.Coinage -> "Coinage"
        is SendPlanDebugInfo.External -> "External"
    }
    PolkadotSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PolkadotTheme.spacings.mediumIncreased, vertical = PolkadotTheme.spacings.small),
        shape = PolkadotTheme.shapes.small,
        color = Color(0x0FFFFFFF)
    ) {
        Column(modifier = Modifier.padding(PolkadotTheme.spacings.extraMedium)) {
            NovaText(text = "[DEBUG] $label: ${info.strategyName}", style = PolkadotTheme.typography.body.small)

            if (info.details.isNotEmpty()) {
                VerticalSpacer { tiny }
                info.details.forEach { line ->
                    NovaText(text = line, style = PolkadotTheme.typography.label.small)
                }
            }
        }
    }
}

@Preview
@Composable
private fun SendEnterAmountScreenPreview() {
    CompositionLocalProvider(
        LocalTokenAmountFormatter provides TokenAmountFormatter.mocked
    ) {
        PolkadotTheme {
            SendEnterAmountScreenInternal(
                state = SendEnterAmountUiState(
                    input = "228.69",
                    recipient = "2o4ytihgkgrjbsk4kjb45lnqlkn35lk3ny73l54jnu45lkjulk5u4lu4lubhv",
                    recipientType = ExtractedAddress.DisplayType.ADDRESS,
                    available = TokenAmountModel.mock,
                    isSendInProgress = false,
                    showBalanceError = true,
                    isSendEnabled = false,
                    isAmountLocked = false,
                    recipientAvatarColor = AvatarColorScheme.Garnet
                ),
                onAmountChange = {},
                onConfirmClick = {},
                onBackClick = {},
                onInfoClick = {}
            )
        }
    }
}
