package io.paritytech.polkadotapp.feature_products_impl.domain.topUpRequest

import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.common.data.time.TimeProvider
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.getOrEmpty
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.CoinageTransactionService
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_impl.data.storage.TopUpSourceStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.ExperimentalTime

private const val COINAGE_LOG_TAG = "CoinageTransfer"

/**
 * The top-ups products have asked for, and how far each has got.
 *
 * Registering and following are separate because a top-up outlives the call that asked for it: the product
 * is told the operation exists, and then watches it — possibly from a later process, possibly after being
 * killed halfway through.
 */
interface TopUpService {
    /**
     * Records the top-up and starts it, returning as soon as it is registered rather than when it finishes.
     *
     * Fails with [TopUpError.AlreadyExists] if [id] is taken, [TopUpError.SourceBusy] if another top-up is
     * still drawing on [source], and [TopUpError.InvalidSource] if the source cannot be resolved.
     */
    suspend fun start(
        productId: ProductId,
        id: PaymentTopUpId,
        amount: Balance,
        source: PaymentTopUpSource,
    ): Result<Unit>

    /**
     * Follows a registered top-up, resuming it first if this process has not been running it.
     *
     * Fails with [TopUpError.NotFound] for an id this product never registered. Never completes on its own:
     * a terminal status stands until the product stops listening.
     */
    fun status(productId: ProductId, id: PaymentTopUpId): Flow<TopUpStatus>

    /**
     * Picks up every top-up left unfinished by a previous run. Called once at start-up.
     *
     * Without it a top-up only resumes when a product happens to ask after it, which leaves money sitting on
     * a key for as long as that product goes unopened — and the window would expire meanwhile, turning a
     * top-up that was merely interrupted into one that terminally failed.
     */
    suspend fun resumeUnfinished()
}

@Singleton // Important - stateful service!
@OptIn(ExperimentalTime::class)
class RealTopUpService @Inject constructor(
    private val sourceStorage: TopUpSourceStorage,
    private val transactionService: CoinageTransactionService,
    private val executeTopUpUseCase: ExecuteTopUpUseCase,
    private val sourceResolver: TopUpSourceResolver,
    private val acknowledgements: TopUpAcknowledgementPresenter,
    private val timeProvider: TimeProvider,
    dispatchers: CoroutineDispatchers,
) : TopUpService {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.computation)

    /**
     * One runner per operation, so two followers watch the same run rather than starting a second one
     * against the same money.
     */
    private val running = mutableMapOf<String, StateFlow<TopUpStatus>>()
    private val runningGuard = Mutex()

    override suspend fun start(
        productId: ProductId,
        id: PaymentTopUpId,
        amount: Balance,
        source: PaymentTopUpSource,
    ): Result<Unit> = runningGuard.withLock {
        if (findOperation(productId, id) != null) {
            Timber.tag(COINAGE_LOG_TAG).w("Top-up id already taken product=${productId.value} id=${id.value}")

            return Result.failure(TopUpError.AlreadyExists(id))
        }

        claimantOf(productId, source)?.let { busyWith ->
            Timber.tag(COINAGE_LOG_TAG).w("Top-up source busy product=${productId.value} busyWith=${busyWith.value}")

            return Result.failure(TopUpError.SourceBusy(busyWith))
        }

        val operation = TopUpOperation(id, productId, amount, timeProvider.now())

        return sourceResolver.resolve(productId, source)
            .recoverCatching { throw TopUpError.InvalidSource(it) }
            // Held before a single transaction is built. Until one is registered the ledger has never heard
            // of this top-up, so nothing else could tell a product it exists.
            .flatMap { resolved -> sourceStorage.put(operation.groupId(), source).map { resolved } }
            .onSuccess { resolved -> run(operation, resolved) }
            .map { }
    }

    override fun status(productId: ProductId, id: PaymentTopUpId): Flow<TopUpStatus> = flow {
        val statuses = runningGuard.withLock {
            val operation = findOperation(productId, id) ?: throw TopUpError.NotFound(id)

            running[operation.groupId().value] ?: attach(operation)
        }

        emitAll(statuses)
    }

    override suspend fun resumeUnfinished() {
        val held = sourceStorage.held()

        Timber.tag(COINAGE_LOG_TAG).i("Top-ups to resume on launch: ${held.size}")

        // A held source is exactly an unfinished top-up: it is dropped the moment a verdict is reached.
        held.forEach { (groupId, source) ->
            val operation = groupId.asTopUpOperation() ?: return@forEach

            runningGuard.withLock {
                if (!running.containsKey(groupId.value)) resolveAndRun(operation, source)
            }
        }
    }

    /**
     * The operation itself, read back out of the group id it was written into.
     *
     * Two places know a top-up exists: the ledger, once a transaction is registered, and the held source,
     * from the moment it is asked for. Between them they cover a top-up's whole life.
     */
    private suspend fun findOperation(productId: ProductId, id: PaymentTopUpId): TopUpOperation? {
        val prefix = topUpGroupPrefixOf(productId, id)

        val heldGroup = sourceStorage.held().firstOrNull { it.groupId.value.startsWith(prefix) }?.groupId
        val groupId = heldGroup
            ?: transactionService.getOperationGroupsMatching(prefix)
                .logFailure("Failed to look up the top-up group for ${id.value}")
                .getOrEmpty()
                .keys
                .firstOrNull()

        return groupId?.asTopUpOperation()
    }

    /**
     * The unfinished top-up already drawing on [source], if there is one.
     *
     * A held source is exactly an unfinished top-up, so the sources still held are the whole field of play —
     * one that has reached a verdict has let go of its money and cannot race anybody for it.
     */
    private suspend fun claimantOf(productId: ProductId, source: PaymentTopUpSource): PaymentTopUpId? {
        return sourceStorage.held()
            .firstOrNull { held ->
                val holder = held.groupId.asTopUpOperation() ?: return@firstOrNull false

                source.drawsOnSameFundsAs(held.source, sameProduct = holder.productId == productId)
            }
            ?.groupId
            ?.asTopUpOperation()
            ?.id
    }

    /** Runs an unfinished top-up, or reports the verdict of one whose source has already been dropped. */
    private suspend fun attach(operation: TopUpOperation): StateFlow<TopUpStatus> {
        val groupId = operation.groupId()
        val held = sourceStorage.held().firstOrNull { it.groupId == groupId }
            ?: return MutableStateFlow(executeTopUpUseCase.verdictOf(operation))

        return resolveAndRun(operation, held.source).getOrElse { throw it }
    }

    private suspend fun resolveAndRun(
        operation: TopUpOperation,
        source: PaymentTopUpSource,
    ): Result<StateFlow<TopUpStatus>> {
        Timber.tag(COINAGE_LOG_TAG)
            .i("Top-up resuming product=${operation.productId.value} id=${operation.id.value}")

        return sourceResolver.resolve(operation.productId, source)
            .recoverCatching { throw TopUpError.InvalidSource(it) }
            .map { resolved -> run(operation, resolved) }
    }

    private fun run(operation: TopUpOperation, source: TopUpSource): StateFlow<TopUpStatus> {
        val statuses = MutableStateFlow<TopUpStatus>(TopUpStatus.Detecting)
        running[operation.groupId().value] = statuses

        // On the service's own scope, not the caller's: the product is not waiting on this, and a top-up
        // must keep going while nothing is listening to it.
        scope.launch {
            executeTopUpUseCase.execute(operation, source)
                .onEach { statuses.value = it }
                .onCompletion { settle(operation, statuses.value) }
                .collect()
        }

        return statuses
    }

    /**
     * Drops the source, which is what bounds how long a product's secret keys are kept: they can only ever
     * build another attempt, and there will not be another one. The ledger is the record from here on.
     */
    private suspend fun settle(operation: TopUpOperation, outcome: TopUpStatus) {
        if (!outcome.isTerminal) {
            // The run ended without a verdict — a chain that stalled past the window, say. The source stays,
            // so the next launch picks it up, by which time finality has almost certainly arrived.
            Timber.tag(COINAGE_LOG_TAG).w("Top-up ended without a verdict id=${operation.id.value} last=$outcome")

            return
        }

        sourceStorage.remove(operation.groupId())

        acknowledgements.acknowledge(operation, outcome)
    }
}

/**
 * Whether two sources are the same money, and so cannot be claimed at the same time.
 *
 * A derivation index means nothing outside the product whose subtree it indexes, so two of those are the
 * same source only when the product is too. Coin keys are the money itself rather than a way to reach it, so
 * a single key in common is enough: both top-ups would submit a claim for that coin and one would be refused.
 */
private fun PaymentTopUpSource.drawsOnSameFundsAs(other: PaymentTopUpSource, sameProduct: Boolean): Boolean =
    when {
        this is PaymentTopUpSource.ProductAccount && other is PaymentTopUpSource.ProductAccount ->
            sameProduct && index == other.index

        this is PaymentTopUpSource.PrivateKey && other is PaymentTopUpSource.PrivateKey -> key == other.key

        this is PaymentTopUpSource.Coins && other is PaymentTopUpSource.Coins ->
            secretKeys.any { it in other.secretKeys }

        else -> false
    }
