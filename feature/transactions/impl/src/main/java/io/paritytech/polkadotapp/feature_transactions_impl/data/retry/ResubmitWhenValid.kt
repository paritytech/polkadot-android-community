package io.paritytech.polkadotapp.feature_transactions_impl.data.retry

import io.novasama.substrate_sdk_android.runtime.extrinsic.signer.SendableExtrinsic
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.chains.repository.ChainStateRepository
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_transactions.api.data.retry.ExtrinsicSubmissionFailure
import io.paritytech.polkadotapp.feature_transactions.api.data.retry.ExtrinsicSubmissionFailureRecovery
import io.paritytech.polkadotapp.feature_transactions.api.data.retry.ExtrinsicSubmissionFailureRecoveryStrategy
import io.paritytech.polkadotapp.feature_transactions_impl.data.validation.ExtrinsicValidator
import io.paritytech.polkadotapp.feature_transactions_impl.data.validation.TransactionValidity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.withIndex
import timber.log.Timber

/**
 * Fork-protection recovery strategy: re-validates the failed extrinsic against the chain head on every new
 * block and resubmits it once the runtime reports it valid. Bounded by the extrinsic's mortality (it
 * eventually validates as `Stale`) and, when set, by [maxAttempts].
 */
class ResubmitWhenValid(
    private val chainId: ChainId,
    private val maxAttempts: Int?,
    private val extrinsicValidator: ExtrinsicValidator,
    private val chainStateRepository: ChainStateRepository,
) : ExtrinsicSubmissionFailureRecoveryStrategy {
    override suspend fun recoverSubmissionFailure(
        extrinsic: SendableExtrinsic,
        failure: ExtrinsicSubmissionFailure,
    ): ExtrinsicSubmissionFailureRecovery {
        Timber.i("Submission failed on $chainId ($failure) — starting validity-driven retry")

        return chainStateRepository.currentRemoteBlockNumberFlow(chainId, sharedRequestsBuilder = null)
            .withIndex()
            .mapNotNull { (attempt, _) -> decideRecovery(extrinsic, attempt) }
            .first()
    }

    private suspend fun decideRecovery(
        extrinsic: SendableExtrinsic,
        attempt: Int,
    ): ExtrinsicSubmissionFailureRecovery? {
        if (maxAttempts != null && attempt >= maxAttempts) {
            Timber.i("Reached maxAttempts ($maxAttempts) on $chainId — aborting")
            return ExtrinsicSubmissionFailureRecovery.Abort
        }

        val blockHash = chainStateRepository.currentBlockHash(chainId)
        val extrinsicBody = extrinsic.bytesWithoutLength.toDataByteArray()
        val validity = extrinsicValidator.validate(chainId, extrinsicBody, blockHash).getOrNull()
        Timber.d("Re-validated extrinsic on $chainId at block $blockHash (attempt $attempt): $validity")

        return when (validity) {
            is TransactionValidity.Valid -> {
                Timber.i("Extrinsic valid again on $chainId — resubmitting")
                ExtrinsicSubmissionFailureRecovery.Resubmit(extrinsic)
            }

            is TransactionValidity.Invalid ->
                if (validity.isMortalityExpired) {
                    Timber.i("Extrinsic mortality expired on $chainId (${validity.reason}) — aborting")
                    ExtrinsicSubmissionFailureRecovery.Abort
                } else {
                    Timber.d("Extrinsic still invalid on $chainId (${validity.reason}) — waiting for next block")
                    null
                }

            // Unknown verdict or a transient validation failure — keep waiting for the next block.
            is TransactionValidity.Unknown, null -> {
                Timber.d("Validity unresolved on $chainId ($validity) — waiting for next block")
                null
            }
        }
    }
}
