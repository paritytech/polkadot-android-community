package io.paritytech.polkadotapp.feature_coinage_api.data.installation

import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageInstallationId

interface CoinageCurrentInstallationRepository {
    /**
     * This installation's id, created on the first call. It is never backed up, so a reinstall or a restore
     * gets a new one.
     */
    suspend fun getOrCreateCurrent(): CoinageInstallationId
}
