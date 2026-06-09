package io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.strategies

import io.novasama.substrate_sdk_android.runtime.extrinsic.call
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.PlannedMemoEntry
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.StrategyType
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinageTransferWalRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.signer.origins.CoinageTransactionOrigins
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.CoinageTransaction
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.TransferWalEntry
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.consumeCoin
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.mintAndHandOffCoins
import io.paritytech.polkadotapp.feature_coinage_impl.domain.service.TransferExecutionService
import io.paritytech.polkadotapp.feature_coinage_impl.domain.transfer.execution.ExtrinsicSubmissionTask
import javax.inject.Inject

class SplitCoinStrategyFactory @Inject constructor(
    private val coinageTransactionOrigins: CoinageTransactionOrigins,
    private val transferWalRepository: CoinageTransferWalRepository,
    private val walEntryBuilder: WalEntryBuilder,
    private val transferExecutionService: TransferExecutionService,
    private val coinageTransactionFactory: CoinageTransaction.Factory
) {
    fun create(
        payload: StrategyType.Split,
        chain: Chain,
    ): SplitCoinStrategy = SplitCoinStrategy(
        coinageTransactionOrigins = coinageTransactionOrigins,
        transferWalRepository = transferWalRepository,
        walEntryBuilder = walEntryBuilder,
        transferExecutionService = transferExecutionService,
        payload = payload,
        chain = chain,
        coinageTransactionFactory = coinageTransactionFactory
    )
}

class SplitCoinStrategy(
    private val coinageTransactionOrigins: CoinageTransactionOrigins,
    private val transferWalRepository: CoinageTransferWalRepository,
    private val walEntryBuilder: WalEntryBuilder,
    private val transferExecutionService: TransferExecutionService,
    payload: StrategyType.Split,
    private val chain: Chain,
    private val coinageTransactionFactory: CoinageTransaction.Factory
) : TransferStrategy {
    private val coinToSplit = payload.splitFrom
    private val recipientDenominations = payload.recipientDenominations
    private val changeDenominations = payload.changeDenominations
    private val exactCoins = payload.exactCoins

    override suspend fun run(): Result<List<PlannedMemoEntry>> {
        return prepareSplit().map { prepared ->
            transferWalRepository.save(prepared.walEntry)
            transferExecutionService.submit(prepared.task)

            prepared.memoEntries
        }
    }

    private suspend fun prepareSplit(): Result<PreparedSplit> {
        val transaction = coinageTransactionFactory.newTransaction()

        return runCatching {
            val outputs = transaction.mintSplitOutputs()

            val walEntry = walEntryBuilder.createNewWalEntry(
                chainId = chain.id,
                inputCoins = listOf(coinToSplit),
                inputVouchers = emptyList(),
                outputCoins = outputs.all
            ).getOrThrow()

            PreparedSplit(
                task = buildTask(walEntry.id, transaction, outputs.all),
                walEntry = walEntry,
                memoEntries = (exactCoins + outputs.recipient).toMemoEntries()
            )
        }.onFailure {
            transaction.rollback(CoinageTransaction.Stage.PREPARATION, it)
        }
    }

    private suspend fun CoinageTransaction.mintSplitOutputs(): TransferOutputs {
        consumeCoin(coinToSplit)
        handOffCoins(exactCoins)
        val recipientCoins = mintAndHandOffCoins(recipientDenominations).getOrThrow()
        val changeCoins = mintCoins(changeDenominations).getOrThrow()
        return TransferOutputs(recipientCoins, changeCoins)
    }

    private suspend fun buildTask(walId: String, transaction: CoinageTransaction, outputCoins: List<Coin>): ExtrinsicSubmissionTask {
        val splitDestinations = buildSplitDestinations(outputCoins)

        return ExtrinsicSubmissionTask(
            walId = walId,
            origin = coinageTransactionOrigins.createAsCoinOrigin(coin = coinToSplit),
            formExtrinsic = {
                call(
                    moduleName = "Coinage",
                    callName = "split",
                    arguments = mapOf("split_into" to splitDestinations),
                )
            },
            transaction = transaction
        )
    }

    private class PreparedSplit(
        val task: ExtrinsicSubmissionTask,
        val walEntry: TransferWalEntry,
        val memoEntries: List<PlannedMemoEntry>
    )

    private fun buildSplitDestinations(coins: List<Coin>) = coins
        .groupBy { it.valueExponent }.entries
        .sortedBy { it.key }
        .map { (exponent, groupCoins) ->
            val accountIds = groupCoins.map { it.accountId.value }
            listOf(
                exponent.value.toBigInteger(),
                accountIds,
            )
        }
}
