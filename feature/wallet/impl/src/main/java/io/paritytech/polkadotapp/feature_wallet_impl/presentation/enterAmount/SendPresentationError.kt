package io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount

import io.paritytech.polkadotapp.common.presentation.ui.errors.PresentationError
import io.paritytech.polkadotapp.common.presentation.ui.errors.StringResPresentationError
import io.paritytech.polkadotapp.common.presentation.ui.errors.UnexpectedPresentationError
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount.domain.SendError
import io.paritytech.polkadotapp.common.R as RCommon

fun SendError.toPresentationError(): PresentationError = when (this) {
    SendError.SettlementTimeout -> StringResPresentationError(RCommon.string.send_error_settlement_timeout)

    SendError.NotEnoughFunds -> StringResPresentationError(RCommon.string.send_error_not_enough_funds)

    SendError.BalanceUnavailable -> StringResPresentationError(RCommon.string.send_error_balance_unavailable)

    is SendError.Unknown -> UnexpectedPresentationError()
}
