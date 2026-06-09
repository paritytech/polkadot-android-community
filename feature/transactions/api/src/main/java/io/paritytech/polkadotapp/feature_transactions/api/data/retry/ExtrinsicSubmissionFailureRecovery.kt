package io.paritytech.polkadotapp.feature_transactions.api.data.retry

import io.novasama.substrate_sdk_android.runtime.extrinsic.signer.SendableExtrinsic

sealed interface ExtrinsicSubmissionFailureRecovery {
    object Abort : ExtrinsicSubmissionFailureRecovery

    class Resubmit(val extrinsic: SendableExtrinsic) : ExtrinsicSubmissionFailureRecovery
}
