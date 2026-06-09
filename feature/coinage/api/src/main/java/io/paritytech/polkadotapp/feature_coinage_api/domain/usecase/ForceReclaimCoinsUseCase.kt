package io.paritytech.polkadotapp.feature_coinage_api.domain.usecase

import io.paritytech.polkadotapp.chains.network.binding.Balance

interface ForceReclaimCoinsUseCase {
    suspend operator fun invoke(): Result<ReclaimOutcome>
}

sealed interface ReclaimOutcome {
    data class Reclaimed(val amount: Balance) : ReclaimOutcome
    data object NothingToReclaim : ReclaimOutcome
}
