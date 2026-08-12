package io.paritytech.polkadotapp.feature_coinage_impl.domain.service

import io.paritytech.polkadotapp.chains.multiNetwork.connection.ChainConnectionRefCounter
import io.paritytech.polkadotapp.chains.multiNetwork.connection.withConnectionEnabled
import io.paritytech.polkadotapp.common.utils.coerceToUnit
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinageTransferWalRepository
import io.paritytech.polkadotapp.feature_coinage_impl.domain.coinageLogD
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.CoinageTransaction
import io.paritytech.polkadotapp.feature_coinage_impl.domain.transfer.execution.ExtrinsicSubmissionTask
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.feature_transactions.api.data.ExtrinsicService
import io.paritytech.polkadotapp.feature_transactions.api.data.flattenExecutionFailure
import io.paritytech.polkadotapp.feature_transactions.api.data.retry.ResubmitWhenValidFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferExecutionService @Inject constructor(
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
    private val extrinsicService: ExtrinsicService,
    private val transferWalRepository: CoinageTransferWalRepository,
    private val chainConnectionRefCounter: ChainConnectionRefCounter,
    private val resubmitWhenValidFactory: ResubmitWhenValidFactory,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun submit(task: ExtrinsicSubmissionTask) {
        scope.launch {
            submitAndAwait(task)
        }
    }

    suspend fun submitAndAwait(task: ExtrinsicSubmissionTask): Result<Unit> {
        return chainConnectionRefCounter.withConnectionEnabled(chainAssetProvider.chainId(), "TransferExecutionService") {
            coinageLogD("TransferExecutionService submitting wal=${task.walId}")

            // Submission is fire-and-forget on a background scope, so retry a transiently-invalidated transfer
            // (e.g. a not-yet-finalized ring proof) for more blocks than the global default before rolling back.
            val recovery = resubmitWhenValidFactory.createForTxInvalidation(chainAssetProvider.chainId(), COINAGE_RECOVERY_MAX_ATTEMPTS)

            extrinsicService
                .submitExtrinsicAndAwaitExecution(chainAssetProvider.chain(), task.origin, submissionFailureRecovery = recovery, formExtrinsic = task.formExtrinsic)
                .flattenExecutionFailure()
                .onSuccess { task.transaction.commit() }
                .onFailure { task.transaction.rollback(CoinageTransaction.Stage.MEMO_SHARED, it) }
                .also { transferWalRepository.delete(task.walId) }
                .coerceToUnit()
        }
    }

    private companion object {
        const val COINAGE_RECOVERY_MAX_ATTEMPTS = 10
    }
}
