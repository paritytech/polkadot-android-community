package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.models

import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.RecyclingStrategyType

data class DigitalDollarCardDetailsUiState(
    val balanceRestore: BalanceRestoreUiState,
    /** Null until the stored preference has been read — the selector stays hidden rather than showing a guess. */
    val privacyMode: RecyclingStrategyType?
)

sealed interface BalanceRestoreUiState {
    data object NotDetermined : BalanceRestoreUiState
    data object SendCash : BalanceRestoreUiState
    data class Restore(val inProgress: Boolean) : BalanceRestoreUiState
}
