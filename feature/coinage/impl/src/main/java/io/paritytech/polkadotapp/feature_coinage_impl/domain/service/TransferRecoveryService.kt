package io.paritytech.polkadotapp.feature_coinage_impl.domain.service

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.chains.network.binding.BlockNumber
import io.paritytech.polkadotapp.chains.network.binding.toBlockNumber
import io.paritytech.polkadotapp.chains.network.rpc.RpcCalls
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.awaitTrue
import io.paritytech.polkadotapp.common.utils.coerceToUnit
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.mapAsync
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.DerivationIndex
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_impl.data.derivation.CoinKeypairDerivation
import io.paritytech.polkadotapp.feature_coinage_impl.data.derivation.getDerivedAccountIds
import io.paritytech.polkadotapp.feature_coinage_impl.data.model.OnChainCoinInfo
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinageTransferWalRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.VoucherRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.storage.CoinsDeepBackupCompletedStorage
import io.paritytech.polkadotapp.feature_coinage_impl.data.storage.VouchersDeepBackupCompletedStorage
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.CheckpointBlock
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.TransferWalEntry
import io.paritytech.polkadotapp.feature_coinage_impl.domain.transfer.ResolveAction
import io.paritytech.polkadotapp.feature_coinage_impl.domain.transfer.WalEntryChainSnapshot
import io.paritytech.polkadotapp.feature_coinage_impl.domain.transfer.WalEntryResolver
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferRecoveryService @Inject constructor(
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
    private val transferWalRepository: CoinageTransferWalRepository,
    private val coinsDeepBackupCompletedStorage: CoinsDeepBackupCompletedStorage,
    private val vouchersDeepBackupCompletedStorage: VouchersDeepBackupCompletedStorage,
    private val rpcCalls: RpcCalls,
    private val coinKeypairDerivation: CoinKeypairDerivation,
    private val coinRepository: CoinRepository,
    private val voucherRepository: VoucherRepository,
    private val walEntryResolver: WalEntryResolver,
) {
    private val applyMutex = Mutex()

    suspend fun recover(): Result<Unit> = runCatching {
        awaitDeepBackupsCompleted()

        val chain = chainAssetProvider.chain()
        val pending = loadPendingEntries(chain.id)
        if (pending.isEmpty()) return@runCatching

        drainPendingOnFinalizedHeads(chain, pending)
    }
        .logFailure("TransferRecoveryService failed")
        .coerceToUnit()

    private suspend fun awaitDeepBackupsCompleted() {
        coinsDeepBackupCompletedStorage.valueFlow().awaitTrue()
        vouchersDeepBackupCompletedStorage.valueFlow().awaitTrue()
    }

    private suspend fun loadPendingEntries(chainId: ChainId): MutableList<TransferWalEntry> =
        transferWalRepository.getAllForChain(chainId)
            .logFailure("TransferRecoveryService failed to read WAL")
            .getOrDefault(emptyList())
            .toMutableList()

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun drainPendingOnFinalizedHeads(chain: Chain, pending: MutableList<TransferWalEntry>) {
        rpcCalls.subscribeFinalizedHeads(chain.id)
            .transformWhile { header ->
                emit(header.number.toBlockNumber())
                pending.isNotEmpty()
            }
            .collect { finalized ->
                val resolvedIds = resolveAll(chain, finalized, pending)
                pending.removeAll { it.id in resolvedIds }
            }
    }

    private suspend fun resolveAll(chain: Chain, finalized: BlockNumber, entries: List<TransferWalEntry>): Set<String> =
        entries.mapAsync { entry -> entry.id.takeIf { tryResolve(chain, finalized, entry) } }
            .filterNotNull()
            .toSet()

    private suspend fun tryResolve(chain: Chain, finalized: BlockNumber, entry: TransferWalEntry): Boolean =
        resolveOne(chain, finalized, entry)
            .logFailure("Resolve failed for entry=${entry.id}")
            .getOrDefault(false)

    private suspend fun resolveOne(chain: Chain, finalized: BlockNumber, entry: TransferWalEntry): Result<Boolean> =
        probe(chain, entry).flatMap { (snapshot, outputCoins) ->
            val action = walEntryResolver.resolve(entry, finalized, snapshot)
            applyMutex.withLock { applyAction(action, outputCoins) }.map { action !is ResolveAction.Wait }
        }

    private suspend fun probe(chain: Chain, entry: TransferWalEntry): Result<Pair<WalEntryChainSnapshot, List<OutputCoinSpec>>> =
        fetchCoinsInfoFor(chain.id, entry.inputCoinIndices)
            .flatMap { inputCoinInfo ->
                fetchCoinsInfoFor(chain.id, entry.expectedOutputCoinIndices)
                    .map { outputCoinInfo -> inputCoinInfo to outputCoinInfo }
            }
            .mapCatching { (inputCoinInfo, outputCoinInfo) ->
                val canonical = isCheckpointStillCanonical(chain.id, entry.checkpoint)

                val outputsPresent = outputCoinInfo.all { it.value != null }
                val coinInputsConsumed = inputCoinInfo.all { it.value == null }
                val voucherInputsConsumed = vouchersConsumed(entry.inputVoucherIndices)
                val inputsOnChain = !(coinInputsConsumed && voucherInputsConsumed)

                val outputCoins = outputCoinInfo.keys.zip(entry.expectedOutputCoinIndices).mapNotNull { (account, index) ->
                    outputCoinInfo[account]?.let { OutputCoinSpec(index = index, accountId = account, info = it) }
                }

                val snapshot = WalEntryChainSnapshot(
                    inputsOnChain = inputsOnChain,
                    outputsOnChain = outputsPresent,
                    checkpointStillCanonical = canonical,
                )

                snapshot to outputCoins
            }

    private suspend fun fetchCoinsInfoFor(
        chainId: ChainId,
        indices: List<DerivationIndex>
    ): Result<Map<AccountId, OnChainCoinInfo?>> {
        val inputCoinAccounts = if (indices.isEmpty()) {
            emptyList()
        } else {
            coinKeypairDerivation.getDerivedAccountIds(indices)
        }

        return if (inputCoinAccounts.isEmpty()) {
            Result.success(emptyMap())
        } else {
            coinRepository.fetchCoinsInfoFor(chainId, inputCoinAccounts)
        }
            .map { info -> inputCoinAccounts.associateWith { info[it] } }
    }

    private suspend fun isCheckpointStillCanonical(chainId: ChainId, checkpoint: CheckpointBlock): Boolean =
        runCatching {
            rpcCalls.getBlockHash(chainId, checkpoint.blockNumber) == checkpoint.blockHash
        }.getOrElse { true }

    private suspend fun vouchersConsumed(voucherIndices: List<DerivationIndex>): Boolean {
        if (voucherIndices.isEmpty()) return true
        val vouchers = voucherRepository.getByRingVrfKeyIndices(voucherIndices)
        if (vouchers.size < voucherIndices.size) return true
        return vouchers.all {
            it.usageState == RecyclerVoucher.UsageState.USED_ON_CHAIN ||
                it.location !is RecyclerVoucher.Location.InRecycler
        }
    }

    private suspend fun applyAction(action: ResolveAction, outputCoins: List<OutputCoinSpec>): Result<Unit> {
        val entry = action.entry
        return when (action) {
            is ResolveAction.Wait -> Result.success(Unit)

            is ResolveAction.CommitOutputs -> runCatching {
                if (outputCoins.isNotEmpty()) {
                    coinRepository.saveAll(outputCoins.map { it.toCoin(Coin.SpentState.NOT_SPENT) })
                }
                markInputsConsumed(entry)
            }
                .flatMap { transferWalRepository.delete(entry.id) }

            is ResolveAction.MarkInputsSpent -> runCatching {
                markInputsConsumed(entry)
            }
                .flatMap { transferWalRepository.delete(entry.id) }

            is ResolveAction.RevertInputs -> runCatching {
                if (entry.expectedOutputCoinIndices.isNotEmpty()) {
                    coinRepository.removeCoins(entry.expectedOutputCoinIndices)
                }
                if (entry.inputCoinIndices.isNotEmpty()) {
                    coinRepository.setSpentStateByDerivationIndices(entry.inputCoinIndices, Coin.SpentState.NOT_SPENT)
                }
                if (entry.inputVoucherIndices.isNotEmpty()) {
                    voucherRepository.setUsageStateByRingVrfKeyIndices(
                        entry.inputVoucherIndices,
                        RecyclerVoucher.UsageState.NOT_USED,
                    )
                }
            }.flatMap { transferWalRepository.delete(entry.id) }
        }
    }

    private suspend fun markInputsConsumed(entry: TransferWalEntry) {
        if (entry.inputCoinIndices.isNotEmpty()) {
            coinRepository.setSpentStateByDerivationIndices(entry.inputCoinIndices, Coin.SpentState.SPENT_ON_CHAIN)
        }
        if (entry.inputVoucherIndices.isNotEmpty()) {
            voucherRepository.setUsageStateByRingVrfKeyIndices(
                entry.inputVoucherIndices,
                RecyclerVoucher.UsageState.USED_ON_CHAIN,
            )
        }
    }

    private fun OutputCoinSpec.toCoin(spentState: Coin.SpentState): Coin = Coin(
        derivationIndex = index,
        valueExponent = ValueExponent(info.value),
        age = Coin.Age.Known(info.age),
        spentState = spentState,
        accountId = accountId,
    )

    private data class OutputCoinSpec(
        val index: DerivationIndex,
        val accountId: AccountId,
        val info: OnChainCoinInfo,
    )
}
