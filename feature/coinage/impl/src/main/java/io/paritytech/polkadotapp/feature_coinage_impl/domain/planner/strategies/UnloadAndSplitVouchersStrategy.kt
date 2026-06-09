package io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.strategies

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchAlias
import io.paritytech.polkadotapp.bandersnatch_crypto.aliasInContext
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.chains.network.binding.BlockHash
import io.paritytech.polkadotapp.chains.repository.ChainStateRepository
import io.paritytech.polkadotapp.chains.util.EncodedArguments.Companion.autoEncodedArgs
import io.paritytech.polkadotapp.chains.util.call
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.PlannedMemoEntry
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.StrategyType
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.isInRecycler
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinAmountBreakdownUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageBalanceConverterUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.data.derivation.VoucherRingDerivation
import io.paritytech.polkadotapp.feature_coinage_impl.data.helpers.FreeUnloadTokenResolver
import io.paritytech.polkadotapp.feature_coinage_impl.data.helpers.UnloadTokenResolverFactory
import io.paritytech.polkadotapp.feature_coinage_impl.data.helpers.createForCollection
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinageTransferWalRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.RecyclerProofDataProvider
import io.paritytech.polkadotapp.feature_coinage_impl.data.signer.context.CoinageSigningContextProvider
import io.paritytech.polkadotapp.feature_coinage_impl.data.signer.origins.CoinageTransactionOrigins
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.CoinageTransaction
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.SplitDestination
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.TransferWalEntry
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.mintAndHandOffCoins
import io.paritytech.polkadotapp.feature_coinage_impl.domain.service.TransferExecutionService
import io.paritytech.polkadotapp.feature_coinage_impl.domain.transfer.execution.ExtrinsicSubmissionTask
import io.paritytech.polkadotapp.feature_members_api.data.model.RingRevision
import io.paritytech.polkadotapp.feature_people_api.domain.PeopleCollection
import io.paritytech.polkadotapp.feature_transactions.api.domain.model.TransactionOrigin
import javax.inject.Inject

class UnloadAndSplitVouchersStrategyFactory @Inject constructor(
    private val chainStateRepository: ChainStateRepository,
    private val originFactory: CoinageTransactionOrigins,
    private val coinageSigningContextProvider: CoinageSigningContextProvider,
    private val voucherRingDerivation: VoucherRingDerivation,
    private val recyclerProofDataProvider: RecyclerProofDataProvider,
    private val unloadTokenResolverFactory: UnloadTokenResolverFactory,
    private val coinRepository: CoinRepository,
    private val transferWalRepository: CoinageTransferWalRepository,
    private val walEntryBuilder: WalEntryBuilder,
    private val transferExecutionService: TransferExecutionService,
    private val coinageTransactionFactory: CoinageTransaction.Factory,
    private val breakdownUseCase: CoinAmountBreakdownUseCase,
    private val balanceConverterUseCase: CoinageBalanceConverterUseCase
) {
    fun create(
        payload: StrategyType.UnloadAndSplit,
        peopleCollection: PeopleCollection,
        chain: Chain,
    ) = UnloadAndSplitVouchersStrategy(
        originFactory = originFactory,
        payload = payload,
        coinageSigningContextProvider = coinageSigningContextProvider,
        voucherRingDerivation = voucherRingDerivation,
        recyclerProofDataProvider = recyclerProofDataProvider,
        freeUnloadTokenResolver = unloadTokenResolverFactory.createForCollection(peopleCollection),
        peopleCollection = peopleCollection,
        chain = chain,
        chainStateRepository = chainStateRepository,
        coinRepository = coinRepository,
        transferWalRepository = transferWalRepository,
        walEntryBuilder = walEntryBuilder,
        transferExecutionService = transferExecutionService,
        coinageTransactionFactory = coinageTransactionFactory,
        breakdownUseCase = breakdownUseCase,
        balanceConverterUseCase = balanceConverterUseCase
    )
}

class UnloadAndSplitVouchersStrategy(
    payload: StrategyType.UnloadAndSplit,
    private val chainStateRepository: ChainStateRepository,
    private val originFactory: CoinageTransactionOrigins,
    private val peopleCollection: PeopleCollection,
    private val coinageSigningContextProvider: CoinageSigningContextProvider,
    private val voucherRingDerivation: VoucherRingDerivation,
    private val recyclerProofDataProvider: RecyclerProofDataProvider,
    private val freeUnloadTokenResolver: FreeUnloadTokenResolver,
    private val chain: Chain,
    private val coinRepository: CoinRepository,
    private val transferWalRepository: CoinageTransferWalRepository,
    private val walEntryBuilder: WalEntryBuilder,
    private val transferExecutionService: TransferExecutionService,
    private val coinageTransactionFactory: CoinageTransaction.Factory,
    private val breakdownUseCase: CoinAmountBreakdownUseCase,
    private val balanceConverterUseCase: CoinageBalanceConverterUseCase
) : TransferStrategy {
    private val vouchers = payload.vouchersToUnload
    private val recipientAmount = payload.recipientAmount
    private val exactCoins = payload.exactCoins

    override suspend fun run(): Result<List<PlannedMemoEntry>> {
        return prepare().map { prepared ->
            transferWalRepository.saveAll(prepared.walEntries)
            prepared.tasks.forEach { transferExecutionService.submit(it) }

            prepared.memoEntries
        }
    }

    private suspend fun prepare(): Result<Prepared> {
        // Existing coins are handed off in a prep-scoped transaction: rolled back only if preparation fails,
        // never on a per-group submission failure (the per-group transactions own that).
        val prepTransaction = coinageTransactionFactory.newTransaction()
        val groupTransactions = mutableListOf<CoinageTransaction>()

        return runCatching {
            require(vouchers.isNotEmpty()) { "TransferStrategyError.emptyVouchers" }
            require(vouchers.all { it.isInRecycler() }) { "TransferStrategyError.missingRecyclerInfo" }

            val batches = resolveBatches()
            val freeUnloadTokens = freeUnloadTokenResolver.resolve(chain.id, batches.size)

            prepTransaction.handOffCoins(exactCoins)

            val recyclerRevisionBlockHash = chainStateRepository.currentBlockHash(chain.id)
            val groupRevisions = recyclerProofDataProvider
                .getRecyclerRevisions(chain.id, batches.map { it.recyclerKey }, recyclerRevisionBlockHash)
                .logFailure("Failed to get recycler revisions")
                .getOrThrow()

            val recipientMemoCoins = mutableListOf<Coin>()
            val tasks = mutableListOf<ExtrinsicSubmissionTask>()
            val walEntries = mutableListOf<TransferWalEntry>()

            batches.forEachIndexed { index, batch ->
                val transaction = coinageTransactionFactory.newTransaction()
                val outputs = transaction.mintGroupOutputs(batch)
                groupTransactions.add(transaction)

                val walEntry = walEntryBuilder.createNewWalEntry(
                    chainId = chain.id,
                    inputCoins = emptyList(),
                    inputVouchers = batch.vouchers,
                    outputCoins = outputs.all
                ).getOrThrow()

                tasks.add(
                    buildTask(
                        walId = walEntry.id,
                        batch = batch,
                        transaction = transaction,
                        outputCoins = outputs.all,
                        unloadToken = freeUnloadTokens[index],
                        recyclerRevisionBlockHash = recyclerRevisionBlockHash,
                        revision = groupRevisions.getValue(batch.recyclerKey)
                    )
                )
                walEntries.add(walEntry)
                recipientMemoCoins.addAll(outputs.recipient)
            }

            Prepared(
                tasks = tasks,
                walEntries = walEntries,
                memoEntries = (exactCoins + recipientMemoCoins).toMemoEntries()
            )
        }.onFailure { cause ->
            groupTransactions.forEach { it.rollback(CoinageTransaction.Stage.PREPARATION, cause) }
            prepTransaction.rollback(CoinageTransaction.Stage.PREPARATION, cause)
        }
    }

    private suspend fun resolveBatches(): List<VoucherBatch> {
        val breakdown = breakdownUseCase.createCoinAmountBreakdown().getOrThrow()
        val conversionContext = balanceConverterUseCase.create().getOrThrow()
        val maxConsolidation = coinRepository.fetchMaxConsolidation(chain.id).getOrThrow()

        return VoucherBatchDistribution.distribute(
            vouchers = vouchers,
            recipientAmount = recipientAmount,
            maxConsolidation = maxConsolidation,
            breakdown = breakdown,
            conversionContext = conversionContext
        )
    }

    private suspend fun CoinageTransaction.mintGroupOutputs(batch: VoucherBatch): TransferOutputs {
        useVouchers(batch.vouchers)
        val recipientCoins = mintAndHandOffCoins(batch.recipientDenominations).getOrThrow()
        val changeCoins = mintCoins(batch.changeDenominations).getOrThrow()
        return TransferOutputs(recipientCoins, changeCoins)
    }

    private suspend fun buildTask(
        walId: String,
        batch: VoucherBatch,
        transaction: CoinageTransaction,
        outputCoins: List<Coin>,
        unloadToken: FreeUnloadTokenResolver.ResolvedUnloadToken,
        recyclerRevisionBlockHash: BlockHash,
        revision: RingRevision
    ): ExtrinsicSubmissionTask {
        val destinations = buildSplitDestinations(outputCoins)
        val origin = makeOriginDefinition(batch.vouchers, unloadToken, recyclerRevisionBlockHash)
        val aliases = buildAliases(batch.vouchers)

        return ExtrinsicSubmissionTask(
            walId = walId,
            origin = origin,
            formExtrinsic = {
                call(
                    moduleName = "Coinage",
                    callName = "unload_recycler_into_coins",
                    arguments = autoEncodedArgs(
                        "aliases" to aliases,
                        "value" to batch.recyclerKey.exponent,
                        "index" to batch.recyclerKey.recyclerIndex,
                        "revision" to revision,
                        "split_into" to destinations,
                        "max_fee" to Balance.ZERO,
                    ),
                )
            },
            transaction = transaction
        )
    }

    private fun buildSplitDestinations(coins: List<Coin>): List<SplitDestination> {
        return coins.groupBy { it.valueExponent }
            .entries
            .sortedBy { it.key }
            .map { (exponent, groupCoins) ->
                SplitDestination(
                    exponent = exponent,
                    accountIds = groupCoins.map { it.accountId },
                )
            }
    }

    private suspend fun buildAliases(vouchers: List<RecyclerVoucher>): List<BandersnatchAlias> {
        val aliasContext = coinageSigningContextProvider.recyclerVouchersContext()

        return vouchers.map { voucher ->
            voucherRingDerivation.deriveBandersnatch(voucher.ringVrfKeyIndex)
                .aliasInContext(aliasContext)
        }
    }

    private fun makeOriginDefinition(
        vouchers: List<RecyclerVoucher>,
        resolvedUnloadToken: FreeUnloadTokenResolver.ResolvedUnloadToken,
        recyclerRevisionBlockHash: BlockHash,
    ): TransactionOrigin {
        return originFactory.createAsUnloadTokenPeopleOrigin(
            vouchers = vouchers,
            resolvedUnloadToken = resolvedUnloadToken,
            recyclerRevisionBlockHash = recyclerRevisionBlockHash,
            peopleCollection = peopleCollection,
        )
    }

    private class Prepared(
        val tasks: List<ExtrinsicSubmissionTask>,
        val walEntries: List<TransferWalEntry>,
        val memoEntries: List<PlannedMemoEntry>
    )
}
