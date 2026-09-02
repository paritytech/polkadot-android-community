@file:OptIn(ExperimentalTime::class)

package io.paritytech.polkadotapp.feature_transaction_storage_impl.domain.slotAllocator

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.chains.multiNetwork.connection.ChainConnectionRefCounter
import io.paritytech.polkadotapp.chains.multiNetwork.connection.withConnectionEnabled
import io.paritytech.polkadotapp.chains.network.rpc.RpcCalls
import io.paritytech.polkadotapp.common.data.cache.CacheableDataConsistency
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.coerceToUnit
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.mapError
import io.paritytech.polkadotapp.common.utils.mapErrorNotInstance
import io.paritytech.polkadotapp.common.utils.orZero
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector
import io.paritytech.polkadotapp.common.utils.progressStallReport.markRegion
import io.paritytech.polkadotapp.feature_chain_resources_api.data.api.claimLongTermStorage
import io.paritytech.polkadotapp.feature_chain_resources_api.data.api.resourcesCalls
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTldProvider
import io.paritytech.polkadotapp.feature_dotns_api.domain.getTldRetrying
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import io.paritytech.polkadotapp.common.R as RCommon

class RealTransactionStorageSlotAllocator @Inject constructor(
    private val chainRegistry: ChainRegistry,
    private val knownChains: KnownChains,
    private val extrinsicService: ExtrinsicService,
    private val transactionStorageOrigins: TransactionStorageOrigins,
    private val longTermStorageSlotRepository: LongTermStorageSlotRepository,
    private val transactionStorageRepository: TransactionStorageRepository,
    private val bandersnatchKeyResolver: BandersnatchKeyResolver,
    private val activePeopleCollectionUseCase: ActivePeopleCollectionUseCase,
    private val chainConnectionRefCounter: ChainConnectionRefCounter,
    private val dotNsTldProvider: DotNsTldProvider,
    private val rpcCalls: RpcCalls,
) : TransactionStorageSlotAllocator {
    /**
     * The claim is submitted on the **people** chain, but the resulting allowance is propagated to
     * the **bullet-in** chain automatically — there is no separate allocate step on bullet-in.
     * That is why we read [currentBulletInAllocation] and await visibility via
     * [awaitAllocationVisibleOnBulletIn] against bullet-in, while the extrinsic itself runs on people.
     */
    context(diagnostics: StalenessReportCollector)
    override suspend fun allocate(target: AccountId, strategy: OnExistingAllocationStrategy): Result<Unit> = diagnostics.markRegion(RCommon.string.transaction_storage_stall_allocating) {
        Timber.i("starting allocate for slotAccountKey, strategy=$strategy")

        chainConnectionRefCounter.withConnectionEnabled(
            chainIds = setOf(knownChains.people, knownChains.bulletIn),
            label = CONNECTION_LABEL
        ) {
            resolveAllocateContext(target).flatMap { ctx ->
                if (strategy == OnExistingAllocationStrategy.IGNORE && ctx.previousCount > BigInteger.ZERO) {
                    Timber.i("existing allocation=${ctx.previousCount}; strategy=IGNORE — skipping claim")
                    return@flatMap Result.success(Unit)
                }
                pickFreeCounter(ctx.claim.chain.id, ctx.claim.period, ctx.claim.collection)
                    .mapError { TransactionStorageSlotAllocationError.NoAllocationAvailable(it) }
                    .flatMap { counter -> submitClaim(ctx.claim, counter, target) }
                    .onSuccess {
                        awaitAllocationVisibleOnBulletIn(target, ctx.previousCount)
                            .logFailure("Failed to awaitAllocationVisibleOnBulletIn")
                    }
            }
        }
            .onFailure { Timber.e(it, "allocate failed") }
            .mapErrorNotInstance<_, TransactionStorageSlotAllocationError> { TransactionStorageSlotAllocationError.Unknown(it) }
    }

    /**
     * Uses `HopRuntimeApi.can_account_promote` instead of the `Authorizations` storage entry: HOP promotion
     * requires an unexpired authorization; the remaining extent is not checked. A failed runtime call fails
     * the result and does not trigger a claim.
     */
    context(diagnostics: StalenessReportCollector)
    override suspend fun ensurePromotable(target: AccountId): Result<Unit> = diagnostics.markRegion(RCommon.string.transaction_storage_stall_allocating) {
        Timber.i("ensuring active bullet-in authorization for HOP promotion")

        chainConnectionRefCounter.withConnectionEnabled(
            chainIds = setOf(knownChains.people, knownChains.bulletIn),
            label = CONNECTION_LABEL
        ) {
            canPromote(target).flatMap { promotable ->
                if (promotable) {
                    Timber.i("active authorization present on bullet-in — skipping claim")
                    return@flatMap Result.success(Unit)
                }
                resolveClaimContext().flatMap { ctx ->
                    pickFreeCounter(ctx.chain.id, ctx.period, ctx.collection)
                        .mapError { TransactionStorageSlotAllocationError.NoAllocationAvailable(it) }
                        .flatMap { counter -> submitClaim(ctx, counter, target) }
                        .flatMap { awaitPromotableOnBulletIn(target) }
                }
            }
        }
            .onFailure { Timber.e(it, "ensurePromotable failed") }
            .mapErrorNotInstance<_, TransactionStorageSlotAllocationError> { TransactionStorageSlotAllocationError.Unknown(it) }
    }

    context(diagnostics: StalenessReportCollector)
    private suspend fun canPromote(target: AccountId): Result<Boolean> = diagnostics.markRegion(RCommon.string.stall_reading_chain_state) {
        transactionStorageRepository.canAccountPromote(knownChains.bulletIn, target, dataLength = 0u)
            .onSuccess { Timber.i("can_account_promote=$it") }
    }

    /** Evaluates `can_account_promote` once immediately and then on every new bullet-in head. */
    context(diagnostics: StalenessReportCollector)
    private suspend fun awaitPromotableOnBulletIn(target: AccountId): Result<Unit> = diagnostics.markRegion(RCommon.string.transaction_storage_stall_awaiting_bulletin) {
        runCatching {
            withTimeout(AWAIT_PROMOTABLE_TIMEOUT) {
                Timber.i("waiting for bullet-in to report the authorization active")

                rpcCalls.subscribeNewHeads(knownChains.bulletIn)
                    .map { }
                    .onStart { emit(Unit) }
                    .map { transactionStorageRepository.canAccountPromote(knownChains.bulletIn, target, dataLength = 0u).getOrThrow() }
                    .first { it }

                Timber.i("authorization active on bullet-in")
            }
        }
    }

    context(diagnostics: StalenessReportCollector)
    private suspend fun resolveClaimContext(): Result<ClaimContext> = diagnostics.markRegion(RCommon.string.stall_reading_chain_state) {
        runCatching {
            val chain = chainRegistry.getChain(knownChains.people)
            val collection = activePeopleCollectionUseCase.getActivePeopleCollection()
            val period = currentPeriod(chain.id)
            Timber.i("chain=${chain.id}, collection=$collection, period=$period")
            ClaimContext(chain, collection, period)
        }
    }

    context(diagnostics: StalenessReportCollector)
    private suspend fun resolveAllocateContext(target: AccountId): Result<AllocateContext> {
        return resolveClaimContext().mapCatching { claim ->
            val previousCount = currentBulletInAllocation(target)
            Timber.i("pre-tx allocation=$previousCount")
            AllocateContext(claim, previousCount)
        }
    }

    context(diagnostics: StalenessReportCollector)
    private suspend fun submitClaim(ctx: ClaimContext, counter: UByte, target: AccountId): Result<Unit> = diagnostics.markRegion(RCommon.string.stall_submitting_transaction) {
        Timber.i("picked free counter=$counter; submitting claim_long_term_storage extrinsic")
        transactionStorageOrigins.asResourcesLongTermStorage(ctx.period, counter, ctx.collection)
            .flatMap { origin ->
                extrinsicService.submitExtrinsicAndAwaitExecution(ctx.chain, origin) {
                    resourcesCalls.claimLongTermStorage(ctx.period, counter, target)
                }
                    .flattenExecutionFailure()
                    .coerceToUnit()
            }
            .onSuccess { Timber.i("extrinsic executed for counter=$counter") }
    }

    private suspend fun currentBulletInAllocation(target: AccountId): BigInteger {
        return transactionStorageRepository
            .getAuthorization(knownChains.bulletIn, target, CacheableDataConsistency.CONSISTENT_WITH_REMOTE)
            .getOrNull()?.extent?.transactionsAllowance
            .orZero()
    }

    context(diagnostics: StalenessReportCollector)
    private suspend fun awaitAllocationVisibleOnBulletIn(target: AccountId, previousCount: BigInteger): Result<Unit> = diagnostics.markRegion(RCommon.string.transaction_storage_stall_awaiting_bulletin) {
        runCatching {
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

    context(diagnostics: StalenessReportCollector)
    private suspend fun pickFreeCounter(
        chainId: ChainId,
        period: UInt,
        collection: PeopleCollection,
    ): Result<UByte> = diagnostics.markRegion(RCommon.string.transaction_storage_stall_picking_slot) {
        longTermStorageSlotRepository.maxClaimsPerPeriod().mapCatching { maxCounters ->
            val tld = dotNsTldProvider.getTldRetrying()
            Timber.i("scanning $maxCounters counters for period=$period")
            val aliasesByCounter = (0u until maxCounters.toUInt()).associateWith { c ->
                val context = BandersnatchContext.longTermStorageClaim(tld, period, c.toUByte())
                bandersnatchKeyResolver.getAliasInContext(collection, context)
            }
            val taken = longTermStorageSlotRepository.spentAliases(chainId, period, aliasesByCounter.values.toList())
            Timber.i("${taken.size}/$maxCounters counters already claimed")
            val freeCounter = aliasesByCounter.entries.firstOrNull { (_, alias) -> alias !in taken }
                ?: error("No more slots available: all $maxCounters counters are claimed for period=$period")
            freeCounter.key.toUByte()
        }
    }

    private suspend fun currentPeriod(chainId: ChainId): UInt {
        val periodSeconds = longTermStorageSlotRepository.periodDurationSeconds(chainId).toLong()
        return (Clock.System.now().epochSeconds / periodSeconds).toUInt()
    }

    private data class ClaimContext(
        val chain: Chain,
        val collection: PeopleCollection,
        val period: UInt,
    )

    private data class AllocateContext(
        val claim: ClaimContext,
        val previousCount: BigInteger,
    )

    companion object {
        val AWAIT_BULLETIN_TIMEOUT = 30.seconds
        val AWAIT_PROMOTABLE_TIMEOUT = 60.seconds
        private const val CONNECTION_LABEL = "TransactionStorageSlotAllocator"
    }
}
