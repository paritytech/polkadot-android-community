package io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase

import io.novasama.substrate_sdk_android.encrypt.keypair.Keypair
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.coerceToUnit
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.flattenResult
import io.paritytech.polkadotapp.common.utils.mapAsync
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_impl.data.blockchain.coinage
import io.paritytech.polkadotapp.feature_coinage_impl.data.blockchain.transfer
import io.paritytech.polkadotapp.feature_coinage_impl.data.model.OnChainCoinInfo
import io.paritytech.polkadotapp.feature_coinage_impl.data.signer.origins.CoinageTransactionOrigins
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.CoinageTransaction
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.mintCoin
import io.paritytech.polkadotapp.feature_coinage_impl.domain.service.TransferExecutionService
import io.paritytech.polkadotapp.feature_coinage_impl.domain.transfer.execution.ExtrinsicSubmissionTask
import java.util.UUID
import javax.inject.Inject

interface CoinageTransferSubmissionUseCase {
    suspend operator fun invoke(keyPairs: List<Keypair>, coinsInfo: Map<AccountId, OnChainCoinInfo>): Result<Unit>
}

class RealCoinageTransferSubmissionUseCase @Inject constructor(
    private val coinageTransactionOrigins: CoinageTransactionOrigins,
    private val transferExecutionService: TransferExecutionService,
    private val coinageTransactionFactory: CoinageTransaction.Factory,
) : CoinageTransferSubmissionUseCase {
    override suspend operator fun invoke(keyPairs: List<Keypair>, coinsInfo: Map<AccountId, OnChainCoinInfo>): Result<Unit> {
        return keyPairs.mapAsync { keyPair ->
            coinsInfo[keyPair.publicKey.toDataByteArray()]?.let { info ->
                buildTaskFor(ValueExponent(info.value), keyPair)
                    .flatMap { transferExecutionService.submitAndAwait(it) }
            } ?: Result.success(Unit)
        }
            .flattenResult()
            .coerceToUnit()
    }

    private suspend fun buildTaskFor(valueExponent: ValueExponent, keypair: Keypair): Result<ExtrinsicSubmissionTask> {
        val transaction = coinageTransactionFactory.newTransaction()

        return transaction.mintCoin(valueExponent).mapCatching { coin ->
            ExtrinsicSubmissionTask(
                walId = UUID.randomUUID().toString(),
                origin = coinageTransactionOrigins.createAsCoinOrigin(keypair),
                formExtrinsic = {
                    coinage.transfer(coin.accountId)
                },
                transaction = transaction
            )
        }
    }
}
