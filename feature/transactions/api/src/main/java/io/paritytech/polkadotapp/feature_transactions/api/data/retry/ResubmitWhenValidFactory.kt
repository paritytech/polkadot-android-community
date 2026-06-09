package io.paritytech.polkadotapp.feature_transactions.api.data.retry

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId

interface ResubmitWhenValidFactory {
    /**
     * Creates an [ExtrinsicSubmissionFailureRecoveryStrategy] that, on submission failure, re-validates the
     * extrinsic against [chainId]'s head on every new block and resubmits it once the runtime reports it valid.
     *
     * @param maxAttempts caps the total number of block-tick validations before giving up; `null` = unbounded.
     *  Callers submitting an immortal extrinsic must pass a non-null value, otherwise the loop never terminates.
     */
    fun create(chainId: ChainId, maxAttempts: Int? = null): ExtrinsicSubmissionFailureRecoveryStrategy
}
