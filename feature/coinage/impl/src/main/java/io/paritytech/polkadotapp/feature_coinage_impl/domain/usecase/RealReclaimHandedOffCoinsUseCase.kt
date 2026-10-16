package io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase

import io.paritytech.polkadotapp.common.data.time.TimeProvider
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.coerceToUnit
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.flattenResult
import io.paritytech.polkadotapp.common.utils.mapToSet
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.plusHop
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.transferHop
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.CoinageTransactionService
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageInput
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageOperationGroupId
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageScheduledTransactionRequest
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageTransactionState
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageAssetsUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.ReclaimHandedOffCoinsUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.TrackedCoin
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.domain.coinageLogI
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.CoinageTransaction
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.mintCoin
import io.paritytech.polkadotapp.feature_coinage_impl.domain.transaction.submission.CoinageSubmissionParams
import io.paritytech.polkadotapp.feature_coinage_impl.domain.transaction.submission.TransferSubmissionParams
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

/**
 * Past this window a reclaim stops waiting for a coin that is gone — the peer took it — and stops rebuilding an
 * attempt that failed for any reason other than expiring. Short, because until then the payment reads as being
 * reclaimed.
 */
private val RECLAIM_RETRY_WINDOW = 5.minutes

@OptIn(ExperimentalTime::class)
class RealReclaimHandedOffCoinsUseCase @Inject constructor(
    private val coinageAssetsUseCase: CoinageAssetsUseCase,
    private val coinRepository: CoinRepository,
    private val transactionService: CoinageTransactionService,
    private val coinageTransactionFactory: CoinageTransaction.Factory,
    private val timeProvider: TimeProvider,
) : ReclaimHandedOffCoinsUseCase {
    override suspend fun reclaim(coins: List<AccountId>, groupId: CoinageOperationGroupId): Result<Unit> {
        val reclaimable = coinageAssetsUseCase.subscribeCoinsBy(coins).first()
            .filter { it.isReclaimable() }
            .map { it.coin }

        coinageLogI("reclaim group=${groupId.value} requested=${coins.size} reclaimable=${reclaimable.size}")

        if (reclaimable.isEmpty()) return Result.success(Unit)

        val params = TransferSubmissionParams(
            buildUntil = timeProvider.now() + RECLAIM_RETRY_WINDOW,
            retryFailures = true,
        )

        return reclaimable.map { scheduledReclaimOf(it, params, bundleSize = reclaimable.size) }
            .flattenResult()
            .flatMap { transactionService.scheduleTransactions(it, groupId) }
            .coerceToUnit()
    }

    override fun subscribeFailedReclaims(groupId: CoinageOperationGroupId): Flow<Set<AccountId>> =
        transactionService.subscribeOperationGroupStatuses(groupId)
            .map { attempts -> attempts.failedCoins() }
            .distinctUntilChanged()
            .map { failed -> coinRepository.getCoinsBy(failed.toList()).mapToSet { it.accountId } }

    private suspend fun scheduledReclaimOf(
        coin: Coin,
        params: TransferSubmissionParams,
        bundleSize: Int,
    ): Result<CoinageScheduledTransactionRequest> {
        val transaction = coinageTransactionFactory.newTransaction()

        return transaction.mintCoin(coin.valueExponent, coin.provenance.plusHop(transferHop(bundleSize))).map {
            transaction.reclaimCoin(coin)
            val assets = transaction.build()

            CoinageScheduledTransactionRequest(
                policy = CoinageSubmissionParams.reclaimPolicy(params),
                inputs = assets.inputs,
                outputs = assets.outputs,
            )
        }
    }

    private fun TrackedCoin.isReclaimable() = coin.isOnChain && state.handedOff && state.consumerStatus == null

    // A retry registers a new attempt for the same coin, so a coin stays failed only while nothing newer is live.
    private fun List<CoinageTransactionState>.failedCoins() =
        coinsOf { it == DurableTxStatus.FAILURE } - coinsOf { it != DurableTxStatus.FAILURE }

    private fun List<CoinageTransactionState>.coinsOf(predicate: (DurableTxStatus) -> Boolean) =
        filter { predicate(it.status) }
            .flatMap { it.inputs }
            .mapNotNullTo(mutableSetOf()) { (it as? CoinageInput.Coin.Own)?.derivationIndex }
}
