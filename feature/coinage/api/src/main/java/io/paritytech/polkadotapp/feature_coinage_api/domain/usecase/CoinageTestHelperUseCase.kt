package io.paritytech.polkadotapp.feature_coinage_api.domain.usecase

interface CoinageTestHelperUseCase {
    suspend fun makeAllVouchersReady()
}
