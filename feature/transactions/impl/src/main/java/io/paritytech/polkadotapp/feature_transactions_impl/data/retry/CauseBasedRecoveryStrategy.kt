package io.paritytech.polkadotapp.feature_transactions_impl.data.retry

import io.novasama.substrate_sdk_android.runtime.extrinsic.signer.SendableExtrinsic
import io.paritytech.polkadotapp.feature_transactions.api.data.retry.ExtrinsicSubmissionFailure
import io.paritytech.polkadotapp.feature_transactions.api.data.retry.ExtrinsicSubmissionFailureRecovery
import io.paritytech.polkadotapp.feature_transactions.api.data.retry.ExtrinsicSubmissionFailureRecoveryStrategy

/**
 * Routes recovery by failure cause: only a post-pool [ExtrinsicSubmissionFailure.TxInvalidation] (the
 * fork/reorg case) is handed to [onTxInvalidation] (typically [ResubmitWhenValid]); a genuine
 * pre-submission rejection or a transport-level submission error aborts immediately so the caller
 * sees the failure fast instead of waiting out the mortality window.
 */
class CauseBasedRecoveryStrategy(
    private val onTxInvalidation: ExtrinsicSubmissionFailureRecoveryStrategy,
) : ExtrinsicSubmissionFailureRecoveryStrategy {
    override suspend fun recoverSubmissionFailure(
        extrinsic: SendableExtrinsic,
        failure: ExtrinsicSubmissionFailure,
    ): ExtrinsicSubmissionFailureRecovery = when (failure) {
        ExtrinsicSubmissionFailure.TxInvalidation -> onTxInvalidation.recoverSubmissionFailure(extrinsic, failure)
        ExtrinsicSubmissionFailure.PreSubmissionValidation,
        is ExtrinsicSubmissionFailure.Submission -> ExtrinsicSubmissionFailureRecovery.Abort
    }
}
