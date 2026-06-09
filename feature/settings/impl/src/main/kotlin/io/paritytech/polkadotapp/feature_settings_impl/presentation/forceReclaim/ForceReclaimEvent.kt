package io.paritytech.polkadotapp.feature_settings_impl.presentation.forceReclaim

import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.TokenAmountModel

sealed interface ForceReclaimEvent {
    data class Reclaimed(val amount: TokenAmountModel) : ForceReclaimEvent
    data object NothingToReclaim : ForceReclaimEvent
    data object Error : ForceReclaimEvent
}
