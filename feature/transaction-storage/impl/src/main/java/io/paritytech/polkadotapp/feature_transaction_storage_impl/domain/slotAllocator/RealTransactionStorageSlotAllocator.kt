@file:OptIn(ExperimentalTime::class)

package io.paritytech.polkadotapp.feature_transaction_storage_impl.domain.slotAllocator

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.common.data.cache.CacheableDataConsistency
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.coerceToUnit
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.mapError
import io.paritytech.polkadotapp.common.utils.mapErrorNotInstance
import io.paritytech.polkadotapp.common.utils.orZero
import io.paritytech.polkadotapp.feature_chain_resources_api.data.api.claimLongTermStorage
import io.paritytech.polkadotapp.feature_chain_resources_api.data.api.resourcesCalls
import io.paritytech.polkadotapp.feature_people_api.domain.BandersnatchKeyResolver
import io.paritytech.polkadotapp.feature_people_api.domain.PeopleCollection
import io.paritytech.polkadotapp.feature_people_api.domain.useCase.ActivePeopleCollectionUseCase
import io.paritytech.polkadotapp.feature_transaction_storage_api.domain.TransactionStorageRepository
import io.paritytech.polkadotapp.feature_transaction_storage_api.domain.model.increasedAllocationAfter
import io.paritytech.polkadotapp.feature_transaction_storage_api.domain.slotAllocator.OnExistingAllocationStrategy
import io.paritytech.polkadotapp.feature_transaction_storage_api.domain.slotAllocator.TransactionStorageSlotAllocationError
import io.paritytech.polkadotapp.feature_transaction_storage_api.domain.slotAllocator.TransactionStorageSlotAllocator
import io.paritytech.polkadotapp.feature_transaction_storage_impl.data.extension.longTermStorageClaim
import io.paritytech.polkadotapp.feature_transaction_storage_impl.data.repository.LongTermStorageSlotRepository
import io.paritytech.polkadotapp.feature_transaction_storage_impl.data.signer.origins.TransactionStorageOrigins
import io.paritytech.polkadotapp.feature_transactions.api.data.ExtrinsicService
import io.paritytech.polkadotapp.feature_transactions.api.data.flattenExecutionFailure
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class RealTransactionStorageSlotAllocator @Inject constructor(
    private val chainRegistry: ChainRegistry,
    private val knownChains: KnownChains,
    private val extrinsicService: ExtrinsicService,
    private val transactionStorageOrigins: TransactionStorageOrigins,
    private val longTermStorageSlotRepository: LongTermStorageSlotRepository,
    private val transactionStorageRepository: TransactionStorageRepository,
    private val bandersnatchKeyResolver: BandersnatchKeyResolver,
    private val activePeopleCollectionUseCase: ActivePeopleCollectionUseCase,
) : TransactionStorageSlotAllocator {
    /**
     * The claim is submitted on the **people** chain, but the resulting allowance is propagated to
     * the **bullet-in** chain automatically — there is no separate allocate step on bullet-in.
     * That is why we read [currentBulletInAllocation] and await visibility via
     * [awaitAllocationVisibleOnBulletIn] against bullet-in, while the extrinsic itself runs on people.
     */
    override suspend fun allocate(target: AccountId, strategy: OnExistingAllocationStrategy): Result<Unit> {
        Timber.i("starting allocate for slotAccountKey, strategy=$strategy")
        return runCatching {
            val chain = chainRegistry.getChain(knownChains.people)
            val collection = activePeopleCollectionUseCase.getActivePeopleCollection()
            val period = currentPeriod(chain.id)
            val previousCount = currentBulletInAllocation(target)
            Timber.i("chain=${chain.id}, collection=$collection, period=$period, pre-tx allocation=$previousCount")
            AllocateContext(chain, collection, period, previousCount)
        }.flatMap { ctx ->
            if (strategy == OnExistingAllocationStrategy.IGNORE && ctx.previousCount > BigInteger.ZERO) {
                Timber.i("existing allocation=${ctx.previousCount}; strategy=IGNORE — skipping claim")
                return@flatMap Result.success(Unit)
            }
            pickFreeCounter(ctx.chain.id, ctx.period, ctx.collection)
                .mapError { TransactionStorageSlotAllocationError.NoAllocationAvailable(it) }
                .flatMap { counter ->
                    Timber.i("picked free counter=$counter; submitting claim_long_term_storage extrinsic")
                    val origin = transactionStorageOrigins.asResourcesLongTermStorage(ctx.period, counter, ctx.collection)
                    extrinsicService.submitExtrinsicAndAwaitExecution(ctx.chain, origin) {
                        resourcesCalls.claimLongTermStorage(ctx.period, counter, target)
                    }
                        .flattenExecutionFailure()
                        .coerceToUnit()
                        .onSuccess { Timber.i("extrinsic executed for counter=$counter") }
                }
                .onSuccess {
                    awaitAllocationVisibleOnBulletIn(target, ctx.previousCount)
                        .logFailure("Failed to awaitAllocationVisibleOnBulletIn")
                }
        }
            .onFailure { Timber.e(it, "allocate failed") }
            .mapErrorNotInstance<_, TransactionStorageSlotAllocationError> { TransactionStorageSlotAllocationError.Unknown(it) }
    }

    private suspend fun currentBulletInAllocation(target: AccountId): BigInteger {
        return transactionStorageRepository
            .getAuthorization(knownChains.bulletIn, target, CacheableDataConsistency.CONSISTENT_WITH_REMOTE)
            .getOrNull()?.extent?.transactionsAllowance
            .orZero()
    }

    private suspend fun awaitAllocationVisibleOnBulletIn(target: AccountId, previousCount: BigInteger): Result<Unit> {
        return runCatching {
            withTimeout(AWAIT_BULLETIN_TIMEOUT) {
                Timber.i("waiting for bullet-in allocation to increase past $previousCount")

                transactionStorageRepository
                    .subscribeAuthorization(knownChains.bulletIn, target, CacheableDataConsistency.CONSISTENT_WITH_REMOTE)
                    .filterNotNull()
                    .first { it.increasedAllocationAfter(previousCount) }

                Timber.i("allocation visible on bullet-in")
            }
        }
    }

    private suspend fun pickFreeCounter(
        chainId: ChainId,
        period: UInt,
        collection: PeopleCollection,
    ): Result<UByte> = runCatching {
        val maxCounters = longTermStorageSlotRepository.maxClaimsPerPeriod(chainId)
        Timber.i("scanning $maxCounters counters for period=$period")
        val aliasesByCounter = (0u until maxCounters.toUInt()).associateWith { c ->
            val context = BandersnatchContext.longTermStorageClaim(period, c.toUByte())
            bandersnatchKeyResolver.getAliasInContext(collection, context)
        }
        val taken = longTermStorageSlotRepository.spentAliases(chainId, period, aliasesByCounter.values.toList())
        Timber.i("${taken.size}/$maxCounters counters already claimed")
        val freeCounter = aliasesByCounter.entries.firstOrNull { (_, alias) -> alias !in taken }
            ?: error("No more slots available: all $maxCounters counters are claimed for period=$period")
        freeCounter.key.toUByte()
    }

    private suspend fun currentPeriod(chainId: ChainId): UInt {
        val periodSeconds = longTermStorageSlotRepository.periodDurationSeconds(chainId).toLong()
        return (Clock.System.now().epochSeconds / periodSeconds).toUInt()
    }

    private data class AllocateContext(
        val chain: Chain,
        val collection: PeopleCollection,
        val period: UInt,
        val previousCount: BigInteger,
    )

    companion object {
        val AWAIT_BULLETIN_TIMEOUT = 30.seconds
    }
}
