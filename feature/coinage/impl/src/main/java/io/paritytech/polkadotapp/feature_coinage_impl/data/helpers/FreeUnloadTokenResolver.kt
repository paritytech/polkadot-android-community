package io.paritytech.polkadotapp.feature_coinage_impl.data.helpers

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.getOrEmpty
import io.paritytech.polkadotapp.feature_coinage_impl.data.signer.context.CoinageSigningContextProvider
import io.paritytech.polkadotapp.feature_coinage_impl.domain.coinageLogD
import io.paritytech.polkadotapp.feature_people_api.domain.PeopleCollection
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

class UnloadTokenResolverFactory @Inject constructor(
    private val consumedTokenChecker: ConsumedTokenChecker,
    private val periodCalculator: UnloadTokenPeriodCalculator,
    private val contextProvider: CoinageSigningContextProvider,
    private val peopleUnloadTokenResolverSource: PeopleUnloadTokenResolverSource,
    private val peopleLiteUnloadTokenResolverSource: PeopleLiteUnloadTokenResolverSource,
) {
    fun createForPeople(): FreeUnloadTokenResolver {
        return RealFreeUnloadTokenResolver(
            consumedTokenChecker,
            periodCalculator,
            contextProvider,
            peopleUnloadTokenResolverSource,
        )
    }

    fun createForPeopleLite(): FreeUnloadTokenResolver {
        return RealFreeUnloadTokenResolver(
            consumedTokenChecker,
            periodCalculator,
            contextProvider,
            peopleLiteUnloadTokenResolverSource,
        )
    }
}

fun UnloadTokenResolverFactory.createForCollection(collection: PeopleCollection): FreeUnloadTokenResolver {
    return when (collection) {
        PeopleCollection.People -> createForPeople()
        PeopleCollection.LitePeople -> createForPeopleLite()
    }
}

interface FreeUnloadTokenResolver {
    data class ResolvedUnloadToken(
        val period: Long,
        val counter: Long,
        val unloadTokenContext: BandersnatchContext
    )

    suspend fun resolve(
        chainId: ChainId,
        requiredQuantity: Int
    ): List<ResolvedUnloadToken>
}

class RealFreeUnloadTokenResolver(
    private val consumedTokenChecker: ConsumedTokenChecker,
    private val periodCalculator: UnloadTokenPeriodCalculator,
    private val contextProvider: CoinageSigningContextProvider,
    private val unloadTokenResolverSource: UnloadTokenResolverSource,
) : FreeUnloadTokenResolver {
    override suspend fun resolve(
        chainId: ChainId,
        requiredQuantity: Int
    ): List<FreeUnloadTokenResolver.ResolvedUnloadToken> {
        val periodDuration = unloadTokenResolverSource.getPeriodDuration(chainId)

        val period = periodCalculator.currentPeriod(periodDuration.seconds)

        val maxCounter = unloadTokenResolverSource.getFreeUnloadTokenLimit(chainId)
            .getOrElse { throw IllegalStateException("Failed to determine free unload token limit", it) }

        val availableCounters = findAvailableCounters(
            chainId = chainId,
            period = period,
            maxCounter = maxCounter,
            requiredQuantity = requiredQuantity
        )

        if (availableCounters.size < requiredQuantity) {
            throw IllegalStateException("Free transfer quota exceeded. Quota resets daily")
        }

        val selected = availableCounters.take(requiredQuantity)

        coinageLogD(
            "Resolved $requiredQuantity free unload token(s) for period $period, " +
                "selected counters ${selected.map { it.counter }}"
        )

        return selected
    }

    private suspend fun findAvailableCounters(
        chainId: ChainId,
        period: Long,
        maxCounter: Long,
        requiredQuantity: Int
    ): List<FreeUnloadTokenResolver.ResolvedUnloadToken> {
        if (maxCounter <= 0 || requiredQuantity <= 0) return emptyList()

        val result = mutableListOf<FreeUnloadTokenResolver.ResolvedUnloadToken>()

        // Fetch consumed-token keys in batches so we don't query the whole counter
        // range when the available low indices already cover the required quantity.
        var batchStart = 0L
        while (batchStart < maxCounter && result.size < requiredQuantity) {
            val batchEnd = minOf(batchStart + BATCH_SIZE, maxCounter)

            val queries = (batchStart until batchEnd).map { counter ->
                val context = contextProvider.freeUnloadTokenContext(period.toInt(), counter.toInt())

                val alias = unloadTokenResolverSource.generateAlias(context.value)

                ConsumedTokenChecker.Query(period, alias.toDataByteArray())
            }

            val notUsedIndices = consumedTokenChecker.getNotUsedCounterIndices(chainId, queries).getOrEmpty()

            notUsedIndices.forEach { index ->
                val counter = batchStart + index

                val counterContext = contextProvider.freeUnloadTokenContext(period.toInt(), counter.toInt())

                result += FreeUnloadTokenResolver.ResolvedUnloadToken(
                    period = period,
                    counter = counter,
                    unloadTokenContext = counterContext
                )
            }

            batchStart = batchEnd
        }

        return result
    }

    private companion object {
        const val BATCH_SIZE = 100L
    }
}
