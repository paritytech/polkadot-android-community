package io.paritytech.polkadotapp.feature_coinage_api.domain.usecase

import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinPrivateKey
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageTransferDetection
import kotlinx.coroutines.flow.Flow

interface CoinageTransferUseCase {
    suspend operator fun invoke(
        transferCoins: Boolean,
        coinKeys: List<CoinPrivateKey>,
        pastDetection: CoinageTransferDetection?
    ): Flow<CoinageTransferDetection>
}
