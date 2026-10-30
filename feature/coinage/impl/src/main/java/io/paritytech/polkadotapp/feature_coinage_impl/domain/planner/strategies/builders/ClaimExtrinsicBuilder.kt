package io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.strategies.builders

import io.novasama.substrate_sdk_android.encrypt.keypair.Keypair
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.feature_coinage_impl.data.blockchain.coinage
import io.paritytech.polkadotapp.feature_coinage_impl.data.blockchain.transfer
import io.paritytech.polkadotapp.feature_coinage_impl.data.signer.origins.CoinageTransactionOrigins
import io.paritytech.polkadotapp.feature_transactions.api.data.EnrichedSendableExtrinsic
import io.paritytech.polkadotapp.feature_transactions.api.data.ExtrinsicService
import javax.inject.Inject

class ClaimExtrinsicBuilder @Inject constructor(
    private val coinageTransactionOrigins: CoinageTransactionOrigins,
    private val extrinsicService: ExtrinsicService,
) {
    suspend fun build(
        chain: Chain,
        coinKeypair: Keypair,
        destination: AccountId,
    ): Result<EnrichedSendableExtrinsic> = extrinsicService.buildExtrinsic(
        chain = chain,
        origin = coinageTransactionOrigins.createAsCoinOrigin(coinKeypair),
        options = ExtrinsicService.SubmissionOptions(),
        formExtrinsic = { coinage.transfer(destination) },
    )
}
