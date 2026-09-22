package io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase

import io.paritytech.polkadotapp.chains.network.binding.BlockNumber
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.reevaluate
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageInstallationId
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.hasEverBeenOnChain
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageAssetsUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinagePaymentState
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinagePaymentStatus
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinagePaymentStatusUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.TrackedCoin
import io.paritytech.polkadotapp.feature_coinage_impl.data.installation.CoinageInstallationRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.transaction.CoinageStateReaderFactory
import io.paritytech.polkadotapp.feature_coinage_impl.domain.coinageLogW
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxStatus
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxStatus.FINALIZED_SUCCESS
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.PinnedChainViewFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * How a payment's coins look to the side that sent them.
 *
 * Two independent facts decide each coin — whether the chain holds it, and what the ledger says minted it —
 * and they arrive from separate streams. Paired at the best head they can disagree for a moment: the ledger
 * records a mint as included before the chain subscription reports the coin present, which reads as a coin
 * that existed and vanished. That is a fine guess to show and a terrible one to close a payment on, so
 * nothing read there is ever marked proven.
 *
 * Proof comes from the finalized chain, where both halves can be read at one vantage point.
 */
class RealCoinagePaymentStatusUseCase @Inject constructor(
    private val coinageAssetsUseCase: CoinageAssetsUseCase,
    private val chainViewFactory: PinnedChainViewFactory,
    private val stateReaderFactory: CoinageStateReaderFactory,
    private val installationRepository: CoinageInstallationRepository,
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
) : CoinagePaymentStatusUseCase {
    override fun subscribeStatuses(coins: List<AccountId>): Flow<Map<AccountId, CoinagePaymentState>> {
        return coinageAssetsUseCase.subscribeCoinsBy(coins)
            .reevaluate(finalizedHeads())
            .map { tracked ->
                val current = installationRepository.getOrCreateCurrent()
                val minterStatuses = tracked.associate { it.coin.accountId to it.effectiveMinterStatus(current) }
                val atFinalized = tracked.presenceAtFinalized(minterStatuses)

                tracked.associate {
                    val status = it.paymentStatus(minterStatuses[it.coin.accountId], atFinalized)

                    it.coin.accountId to CoinagePaymentState(it.coin, status)
                }
            }
            .distinctUntilChanged()
    }

    /**
     * A peer's claim is not our transaction, so nothing local changes when it finalizes — when the mint
     * finalized long before, as with exact coins, only a new finalized head can prove it.
     */
    private fun finalizedHeads(): Flow<BlockNumber> = chainViewFactory.finalizedHeads(chainAssetProvider.chainId())
        // Losing the ticks only loses the re-check at finality; the local streams still drive every status.
        .catch { coinageLogW("Finalized heads subscription failed: $it") }

    /**
     * Whether the finalized chain holds each coin whose mint finalized; absent from the map when unknown.
     *
     * Only those coins are worth asking about: one has to have been minted beyond recall before its absence
     * can mean the peer took it. A read that cannot be taken leaves them unknown, which costs a later look
     * and never a wrong verdict.
     */
    private suspend fun List<TrackedCoin>.presenceAtFinalized(minterStatuses: Map<AccountId, DurableTxStatus?>): Map<AccountId, Boolean> {
        val minted = map { it.coin.accountId }
            .filter { minterStatuses[it] == FINALIZED_SUCCESS }

        if (minted.isEmpty()) return emptyMap()

        val view = chainViewFactory.pin(chainAssetProvider.chainId()).getOrNull() ?: return emptyMap()
        val reader = runCatching { stateReaderFactory.create(view) }.getOrNull() ?: return emptyMap()
        val coins = reader.coinsAt(view.finalizedHead.blockHash, minted).getOrNull() ?: return emptyMap()

        return minted.associateWith { coins[it] != null }
    }
}

/**
 * What the ledger says minted the coin, with one substitution.
 *
 * A coin recovered from a previous installation's backup has no local row for its mint — the transaction was
 * another installation's — yet the recovery scan only ever saves coins the finalized chain already held, so
 * the mint is as final as a recorded one. Reading the missing row as an unfinished mint would leave every
 * such coin at [CoinagePaymentStatus.Detecting] for good.
 */
private fun TrackedCoin.effectiveMinterStatus(currentInstallation: CoinageInstallationId): DurableTxStatus? = when {
    state.minterStatus != null -> state.minterStatus
    coin.derivationIndex.installation != currentInstallation -> FINALIZED_SUCCESS
    else -> null
}

private fun TrackedCoin.paymentStatus(minterStatus: DurableTxStatus?, atFinalized: Map<AccountId, Boolean>): CoinagePaymentStatus = when {
    // Finalized minter and absence at finalized is guaranteed finalized claim
    minterStatus == FINALIZED_SUCCESS && atFinalized[coin.accountId] == false
    -> CoinagePaymentStatus.Claimed(finalized = true)

    // Never minted: the key the peer holds controls nothing, and nothing will change that.
    minterStatus == DurableTxStatus.FAILURE -> CoinagePaymentStatus.Failed

    coin.isOnChain -> CoinagePaymentStatus.AwaitingClaim

    // We have previously seen coin on-chain and now its gone, but its minter has arrived
    // This wont fire if we never seen a coin: worst case we will be stuck at Detecting until finality decides
    coin.hasEverBeenOnChain && minterStatus?.isArrived == true ->
        CoinagePaymentStatus.Claimed(finalized = false)

    // We have not seen a coin unchain on best, but we can see it at finalized
    // Since we cant distinguish between "not on chain" and "we haven't yet synced with best chain" reporting Claimed here would be unsafe
    // So we play conservative and emit AwaitingClaim instead. After coin is gone from finalized block, next pass wil mark it as claimed
    atFinalized[coin.accountId] == true -> CoinagePaymentStatus.AwaitingClaim

    else -> CoinagePaymentStatus.Detecting
}
