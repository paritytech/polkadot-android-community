package io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.strategies

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.util.EncodedArguments.Companion.autoEncodedArgs
import io.paritytech.polkadotapp.chains.util.call
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.StrategyType
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.CoinageTransactionService
import io.paritytech.polkadotapp.feature_coinage_impl.data.signer.origins.CoinageTransactionOrigins
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.CoinageTransaction
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.consumeCoin
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.handOffCoins
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.mintAndHandOffCoins
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.toSplitDestinations
import io.paritytech.polkadotapp.feature_transactions.api.data.ExtrinsicService
import javax.inject.Inject

class SplitCoinStrategyFactory @Inject constructor(
    private val coinageTransactionOrigins: CoinageTransactionOrigins,
    private val extrinsicService: ExtrinsicService,
    private val transactionService: CoinageTransactionService,
    private val coinageTransactionFactory: CoinageTransaction.Factory,
) {
    fun create(
        payload: StrategyType.Split,
        chain: Chain,
    ): SplitCoinStrategy = SplitCoinStrategy(
        coinageTransactionOrigins = coinageTransactionOrigins,
        extrinsicService = extrinsicService,
        transactionService = transactionService,
        payload = payload,
        chain = chain,
        coinageTransactionFactory = coinageTransactionFactory,
    )
}

class SplitCoinStrategy(
    private val coinageTransactionOrigins: CoinageTransactionOrigins,
    private val extrinsicService: ExtrinsicService,
    private val transactionService: CoinageTransactionService,
    payload: StrategyType.Split,
    private val chain: Chain,
    private val coinageTransactionFactory: CoinageTransaction.Factory,
) : TransferStrategy {
    private val coinToSplit = payload.splitFrom
    private val recipientDenominations = payload.recipientDenominations
    private val changeDenominations = payload.changeDenominations
    private val exactCoins = payload.exactCoins

    override suspend fun run(): Result<PreparedTransfer> {
        val transaction = coinageTransactionFactory.newTransaction()

        val outputs = transaction.mintSplitOutputs().getOrElse { return Result.failure(it) }
        val assets = transaction.build()

        val extrinsic = extrinsicService.buildExtrinsic(
            chain = chain,
            origin = coinageTransactionOrigins.createAsCoinOrigin(coin = coinToSplit),
            options = ExtrinsicService.SubmissionOptions(),
            formExtrinsic = {
                call(
                    moduleName = "Coinage",
                    callName = "split",
                    arguments = autoEncodedArgs("split_into" to outputs.all.toSplitDestinations()),
                )
            },
        ).getOrElse { return Result.failure(it) }

        // Reserve first: a key that reaches the recipient without a mark can be selected again and
        // double-spent. The reservation is provisional, so if anything below fails the coins come back.
        val handoffCommit = transactionService.preCommitHandoff(assets.handedOff)
            .getOrElse { return Result.failure(it) }

        return transactionService.submitTransaction(
            extrinsic = extrinsic,
            inputs = assets.inputs,
            outputs = assets.outputs,
            groupId = null,
        ).map { PreparedTransfer((exactCoins + outputs.recipient).toMemoEntries(), handoffCommit) }
    }

    private suspend fun CoinageTransaction.mintSplitOutputs(): Result<TransferOutputs> = runCatching {
        consumeCoin(coinToSplit)
        handOffCoins(exactCoins)
        val recipientCoins = mintAndHandOffCoins(recipientDenominations).getOrThrow()
        val changeCoins = mintCoins(changeDenominations).getOrThrow()
        TransferOutputs(recipientCoins, changeCoins)
    }
}
