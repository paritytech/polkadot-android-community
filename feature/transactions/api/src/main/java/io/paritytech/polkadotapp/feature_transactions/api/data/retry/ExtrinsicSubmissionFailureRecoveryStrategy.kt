package io.paritytech.polkadotapp.feature_transactions.api.data.retry

import io.novasama.substrate_sdk_android.runtime.extrinsic.signer.SendableExtrinsic

interface ExtrinsicSubmissionFailureRecoveryStrategy {
    suspend fun recoverSubmissionFailure(
        extrinsic: SendableExtrinsic,
        failure: ExtrinsicSubmissionFailure
    ): ExtrinsicSubmissionFailureRecovery
}

object Abort : ExtrinsicSubmissionFailureRecoveryStrategy {
    override suspend fun recoverSubmissionFailure(
        extrinsic: SendableExtrinsic,
        failure: ExtrinsicSubmissionFailure
    ): ExtrinsicSubmissionFailureRecovery {
        return ExtrinsicSubmissionFailureRecovery.Abort
    }
}
