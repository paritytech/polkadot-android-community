package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket

import io.paritytech.polkadotapp.common.presentation.ui.errors.PresentationError
import io.paritytech.polkadotapp.common.presentation.ui.errors.StringResPresentationError
import io.paritytech.polkadotapp.common.R as RCommon

class GetCashUnavailablePresentationError :
    PresentationError by StringResPresentationError(RCommon.string.pocket_error_get_cash_unavailable)
