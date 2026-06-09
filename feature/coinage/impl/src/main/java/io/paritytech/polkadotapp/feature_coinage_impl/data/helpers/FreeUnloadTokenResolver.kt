package io.paritytech.polkadotapp.feature_coinage_impl.data.helpers

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.getOrEmpty
import io.paritytech.polkadotapp.feature_coinage_impl.data.signer.context.CoinageSigningContextProvider
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
        val constants = unloadTokenResolverSource.getConstants(chainId)

        // TODO COINAGE: We have to use only current period.
        val periods = periodCalculator.validPeriods(constants.periodDuration.seconds)

        val result = mutableListOf<FreeUnloadTokenResolver.ResolvedUnloadToken>()

        for (period in periods) {
            val availableCointers = findAvailableCounters(
                chainId = chainId,
                period = period,
                maxCounter = constants.maxCounter
            )

            val remaining = requiredQuantity - result.size
            result.addAll(availableCointers.take(remaining))

            if (result.size >= requiredQuantity) break
        }

        if (result.size < requiredQuantity) {
            throw IllegalStateException("Free transfer quota exceeded. Quota resets daily")
        }

        return result
    }

    private suspend fun findAvailableCounters(
        chainId: ChainId,
        period: Long,
        maxCounter: Long
    ): List<FreeUnloadTokenResolver.ResolvedUnloadToken> {
        if (maxCounter <= 0) return emptyList()

        val queries = List(maxCounter.toInt()) { counter ->
            val context = contextProvider.freeUnloadTokenContext(period.toInt(), counter.toInt())

            val alias = unloadTokenResolverSource.generateAlias(context.value)

            ConsumedTokenChecker.Query(period, alias.toDataByteArray())
        }

        val counters = consumedTokenChecker.getNotUsedCounterIndices(chainId, queries)

        return counters
            .getOrEmpty()
            .map { counter ->
                val counterContext = contextProvider.freeUnloadTokenContext(period.toInt(), counter.toInt())

                FreeUnloadTokenResolver.ResolvedUnloadToken(
                    period = period,
                    counter = counter,
                    unloadTokenContext = counterContext
                )
            }
    }
}
