package io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.strategies

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.util.EncodedArguments.Companion.autoEncodedArgs
import io.paritytech.polkadotapp.chains.util.call
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector
import io.paritytech.polkadotapp.common.utils.progressStallReport.markRegion
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinSplit
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.StrategyType
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.CoinageTransactionService
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageOperationGroupId
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageTransactionId
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageTransactionRequest
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.OwnAsset
import io.paritytech.polkadotapp.feature_coinage_impl.data.signer.origins.CoinageTransactionOrigins
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.CoinageTransaction
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.CoinageTransactionAssets
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.consumeCoin
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.mintAndHandOffCoins
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.toSplitDestinations
import io.paritytech.polkadotapp.feature_transactions.api.data.EnrichedSendableExtrinsic
import io.paritytech.polkadotapp.feature_transactions.api.data.ExtrinsicService
import javax.inject.Inject
import io.paritytech.polkadotapp.common.R as RCommon

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
    private val splits = payload.splits
    private val exactCoins = payload.exactCoins

    /**
     * One group of transactions, one per split coin. Every extrinsic is built before anything is reserved, so a
     * failure while building leaves no mark behind.
     */
    context(diagnostics: StalenessReportCollector)
    override suspend fun run(): Result<PreparedTransfer> = diagnostics.markRegion(RCommon.string.coinage_stall_preparing_transfer) {
        if (splits.isEmpty()) return Result.failure(IllegalArgumentException("TransferStrategyError.emptySplits"))

        val prepared = splits.map { split -> prepareSplit(split).getOrElse { return Result.failure(it) } }
        val handedOffExactCoins = exactCoins.map { OwnAsset.Coin(it.derivationIndex) }

        // Reserve first: a key that reaches the recipient without a mark can be selected again and
        // double-spent. The reservation is provisional, so if anything below fails the coins come back.
        val handoffCommit = transactionService
            .preCommitHandoff(handedOffExactCoins + prepared.flatMap { it.assets.handedOff })
            .getOrElse { return Result.failure(it) }

        val requests = prepared.map { split ->
            CoinageTransactionRequest(
                extrinsic = split.extrinsic,
                inputs = split.assets.inputs,
                outputs = split.assets.outputs,
            )
        }

        submitSplits(requests)
            .map { PreparedTransfer((exactCoins + prepared.flatMap { it.recipientCoins }).toMemoEntries(), handoffCommit) }
    }

    private class PreparedSplit(
        val assets: CoinageTransactionAssets,
        val recipientCoins: List<Coin>,
        val extrinsic: EnrichedSendableExtrinsic,
    )

    context(diagnostics: StalenessReportCollector)
    private suspend fun prepareSplit(split: CoinSplit): Result<PreparedSplit> {
        val transaction = coinageTransactionFactory.newTransaction()
        val outputs = transaction.mintSplitOutputs(split).getOrElse { return Result.failure(it) }

        return buildSplitExtrinsic(split.splitFrom, outputs.all).map { extrinsic ->
            PreparedSplit(assets = transaction.build(), recipientCoins = outputs.recipient, extrinsic = extrinsic)
        }
    }

    private suspend fun CoinageTransaction.mintSplitOutputs(split: CoinSplit): Result<TransferOutputs> {
        consumeCoin(split.splitFrom)

        return mintAndHandOffCoins(split.recipientDenominations).flatMap { recipientCoins ->
            mintCoins(split.changeDenominations).map { changeCoins -> TransferOutputs(recipientCoins, changeCoins) }
        }
    }

    /**
     * The origin signs with the split coin's own key, so what takes time here is the runtime, nonce and
     * mortality the builder reads - unlike the unload path, no proof is produced.
     */
    context(diagnostics: StalenessReportCollector)
    private suspend fun buildSplitExtrinsic(
        coinToSplit: Coin,
        outputCoins: List<Coin>,
    ): Result<EnrichedSendableExtrinsic> = diagnostics.markRegion(RCommon.string.stall_reading_chain_state) {
        extrinsicService.buildExtrinsic(
            chain = chain,
            origin = coinageTransactionOrigins.createAsCoinOrigin(coin = coinToSplit),
            options = ExtrinsicService.SubmissionOptions(),
            formExtrinsic = {
                call(
                    moduleName = "Coinage",
                    callName = "split",
                    arguments = autoEncodedArgs("split_into" to outputCoins.toSplitDestinations()),
                )
            },
        )
    }

    context(diagnostics: StalenessReportCollector)
    private suspend fun submitSplits(
        requests: List<CoinageTransactionRequest>,
    ): Result<List<CoinageTransactionId>> = diagnostics.markRegion(RCommon.string.stall_submitting_transaction) {
        transactionService.submitTransactions(requests, CoinageOperationGroupId.generateNew())
    }
}
