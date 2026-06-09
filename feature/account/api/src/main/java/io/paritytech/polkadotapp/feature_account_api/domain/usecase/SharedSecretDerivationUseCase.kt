package io.paritytech.polkadotapp.feature_account_api.domain.usecase

import io.paritytech.polkadotapp.feature_account_api.domain.model.SharedSecretDerivationDomain
import java.security.KeyPair

interface SharedSecretDerivationUseCase {
    suspend fun deriveForDomain(domain: SharedSecretDerivationDomain): KeyPair

    suspend fun generateOneTimeUse(): KeyPair
}
