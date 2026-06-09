package io.paritytech.polkadotapp.feature_coinage_impl.fakeProviders

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.ExponentBoundsRepository

class FakeExponentBoundsRepository(
    val min: Int,
    val max: Int
) : ExponentBoundsRepository {
    override suspend fun minExponent(chainId: ChainId): Result<Int> {
        return Result.success(min)
    }

    override suspend fun maxExponent(chainId: ChainId): Result<Int> {
        return Result.success(max)
    }
}
