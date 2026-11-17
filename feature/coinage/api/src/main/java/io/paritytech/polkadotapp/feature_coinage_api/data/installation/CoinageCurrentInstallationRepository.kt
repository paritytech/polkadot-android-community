package io.paritytech.polkadotapp.feature_coinage_api.data.installation

import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageInstallationId

interface CoinageCurrentInstallationRepository {
    suspend fun getOrCreateCurrent(): CoinageInstallationId
}
