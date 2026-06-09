package io.paritytech.polkadotapp.feature_sso_api.domain

import io.paritytech.polkadotapp.feature_sso_api.domain.model.HandshakeOffer

interface SsoHandshakeUseCase {
    suspend fun respondToOffer(offer: HandshakeOffer, response: HandshakeResponse): Result<Unit>
}

sealed interface HandshakeResponse {
    data object AllowanceAllocation : HandshakeResponse
    data object Success : HandshakeResponse
    data class Failure(val reason: String) : HandshakeResponse
}
