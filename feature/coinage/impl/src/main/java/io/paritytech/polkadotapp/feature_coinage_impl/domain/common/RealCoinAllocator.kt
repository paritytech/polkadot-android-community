package io.paritytech.polkadotapp.feature_coinage_impl.domain.common

import io.paritytech.polkadotapp.feature_coinage_api.domain.common.CoinAllocator
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.DerivationIndex
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_impl.data.derivation.CoinKeypairDerivation
import io.paritytech.polkadotapp.feature_coinage_impl.data.derivation.getDerivedAccountId
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.ExponentBoundsRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.validateValueExponent
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.validateValueExponents
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

class RealCoinAllocator @Inject constructor(
    private val coinRepository: CoinRepository,
    private val keypairDerivation: CoinKeypairDerivation,
    private val boundsRepository: ExponentBoundsRepository,
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider
) : CoinAllocator {
    private val allocationMutex = Mutex()

    override suspend fun allocate(valueExponent: ValueExponent): Result<Coin> =
        allocationMutex.withLock {
            boundsRepository.validateValueExponent(chainAssetProvider.chainId(), valueExponent)
                .map { validExponent ->
                    val derivationIndex = coinRepository.getNextDerivationIndex()
                    val coin = createCoin(derivationIndex, validExponent)
                    coin.apply { coinRepository.save(this) }
                }
        }

    override suspend fun allocateAll(valueExponents: List<ValueExponent>): Result<List<Coin>> = allocationMutex.withLock {
        boundsRepository.validateValueExponents(chainAssetProvider.chainId(), valueExponents)
            .map { validExponents ->
                val nextDerivationIndex = coinRepository.getNextDerivationIndex()

                val coins = validExponents.mapIndexed { index, value ->
                    createCoin(
                        derivationIndex = nextDerivationIndex + index,
                        valueExponent = value
                    )
                }

                coinRepository.saveAll(coins)

                coins
            }
    }

    override suspend fun deallocate(coinIndices: List<DerivationIndex>) {
        coinRepository.removeCoins(coinIndices)
    }

    private suspend fun createCoin(
        derivationIndex: Int,
        valueExponent: ValueExponent
    ): Coin = Coin(
        derivationIndex = derivationIndex,
        valueExponent = valueExponent,
        age = Coin.Age.Unknown,
        spentState = Coin.SpentState.NOT_SPENT,
        accountId = keypairDerivation.getDerivedAccountId(derivationIndex)
    )
}
