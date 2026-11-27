package io.paritytech.polkadotapp.feature_coinage_api.domain.usecase

import io.paritytech.polkadotapp.chains.network.binding.Balance

interface PaymentAuthenticationUseCase {
    suspend fun authenticateIfRequired(amount: Balance): Result<Unit>
}
