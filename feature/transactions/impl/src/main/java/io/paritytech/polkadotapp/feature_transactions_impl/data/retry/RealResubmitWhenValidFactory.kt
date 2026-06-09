package io.paritytech.polkadotapp.feature_transactions_impl.data.retry

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.chains.repository.ChainStateRepository
import io.paritytech.polkadotapp.feature_transactions.api.data.retry.ExtrinsicSubmissionFailureRecoveryStrategy
import io.paritytech.polkadotapp.feature_transactions.api.data.retry.ResubmitWhenValidFactory
import io.paritytech.polkadotapp.feature_transactions_impl.data.validation.ExtrinsicValidator
import javax.inject.Inject

class RealResubmitWhenValidFactory @Inject constructor(
    private val extrinsicValidator: ExtrinsicValidator,
    private val chainStateRepository: ChainStateRepository,
) : ResubmitWhenValidFactory {
    override fun create(chainId: ChainId, maxAttempts: Int?): ExtrinsicSubmissionFailureRecoveryStrategy {
        return ResubmitWhenValid(
            chainId = chainId,
            maxAttempts = maxAttempts,
            extrinsicValidator = extrinsicValidator,
            chainStateRepository = chainStateRepository,
        )
    }
}
