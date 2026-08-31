package io.paritytech.polkadotapp.feature_coinage_impl.domain.recycling

import io.paritytech.polkadotapp.common.data.cache.CacheableDataConsistency
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.toRingCollectionId
import io.paritytech.polkadotapp.feature_members_api.data.repository.MembersRepository
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How many keys a recycler's ring holds, per denomination.
 *
 * Cached for the process: it is chain configuration, so it cannot change under a running app, and the
 * balance would otherwise re-read it on every recomputation.
 */
@Singleton
class RingCapacityProvider @Inject constructor(
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
    private val membersRepository: MembersRepository,
) {
    private val capacities = ConcurrentHashMap<ValueExponent, Int>()

    /**
     * Denominations that could not be read are left out rather than failing the caller, which reads them as
     * rings that are never full — so a strategy waiting on anonymity keeps waiting instead of releasing a
     * voucher on a capacity we do not have.
     */
    suspend fun capacitiesFor(denominations: Set<ValueExponent>): Map<ValueExponent, Int> {
        val chainId = chainAssetProvider.chainId()

        denominations.filterNot(capacities::containsKey).forEach { denomination ->
            membersRepository.getCollection(
                chainId = chainId,
                collectionId = denomination.toRingCollectionId(),
                consistency = CacheableDataConsistency.CAN_BE_STALE,
            )
                .logFailure("Can't fetch ring collection for recycler denomination")
                .onSuccess { capacities[denomination] = it.ringSize.ringCapacity }
        }

        return capacities.filterKeys(denominations::contains)
    }
}
