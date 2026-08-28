package io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.isAgeValidToSpend
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.isInRecycler
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.CoinageTransactionService
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageAssetState
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.OwnAsset
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageAssetsUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.TrackedCoin
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.TrackedVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.isSelectable
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.VoucherRepository
import io.paritytech.polkadotapp.feature_coinage_impl.domain.coinageLogD
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Every join here is deduplicated, because Room invalidates a query when its table is written rather than
 * when the rows it selected change. The presence sync writes every coin in the wallet and the ledger writes
 * on every status change, so without this one unrelated write re-runs the join for every caller — and
 * callers do real work per emission, up to reading the finalized chain.
 */
class RealCoinageAssetsUseCase @Inject constructor(
    private val coinRepository: CoinRepository,
    private val voucherRepository: VoucherRepository,
    private val transactionService: CoinageTransactionService,
) : CoinageAssetsUseCase {
    override fun subscribeCoins(): Flow<List<TrackedCoin>> = combine(
        coinRepository.subscribeAllCoins(),
        transactionService.subscribeAssetStates(),
    ) { coins, states ->
        coins.map { TrackedCoin(it, states.stateOf(OwnAsset.Coin(it.derivationIndex))) }
    }.distinctUntilChanged()

    override fun subscribeCoinsBy(accountIds: List<AccountId>): Flow<List<TrackedCoin>> = combine(
        coinRepository.subscribeCoinsBy(accountIds),
        transactionService.subscribeAssetStates(),
    ) { coins, states ->
        coins.map { TrackedCoin(it, states.stateOf(OwnAsset.Coin(it.derivationIndex))) }
    }.distinctUntilChanged()

    override suspend fun getCoins(): List<TrackedCoin> = subscribeCoins().first()

    override fun subscribeVouchers(): Flow<List<TrackedVoucher>> = combine(
        voucherRepository.subscribeAllVouchers(),
        transactionService.subscribeAssetStates(),
    ) { vouchers, states ->
        vouchers.map { TrackedVoucher(it, states.stateOf(OwnAsset.Voucher(it.ringVrfKeyIndex))) }
    }.distinctUntilChanged()

    override suspend fun getVouchers(): List<TrackedVoucher> = subscribeVouchers().first()

    override suspend fun getSelectableCoins(): List<Coin> {
        val recyclingAge = coinRepository.getCoinRecyclingAge()
        val coins = getCoins()
        val selectable = coins.filter { it.isSelectable(recyclingAge) }

        logCoinSelection(coins, selectable.size, recyclingAge)

        return selectable.map { it.coin }
    }

    override suspend fun getSelectableVouchers(): List<RecyclerVoucher> {
        val vouchers = getVouchers()
        val selectable = vouchers.filter { it.isSelectable() }

        logVoucherSelection(vouchers, selectable.size)

        return selectable.map { it.voucher }
    }

    /** Counts only: this runs for a whole wallet on every selection, so per-asset lines would drown the log. */
    private fun logCoinSelection(coins: List<TrackedCoin>, selectable: Int, recyclingAge: Int) {
        val claimed = coins.count { !it.state.isFree }
        val notOnChain = coins.count { it.state.isFree && !it.coin.isOnChain }
        val tooOld = coins.count {
            it.state.isFree && it.coin.isOnChain && !it.coin.isAgeValidToSpend(recyclableAge = recyclingAge)
        }

        coinageLogD(
            "Coin selection selectable=$selectable of=${coins.size}" +
                " claimed=$claimed notOnChain=$notOnChain tooOld=$tooOld recyclingAge=$recyclingAge"
        )
    }

    private fun logVoucherSelection(vouchers: List<TrackedVoucher>, selectable: Int) {
        val claimed = vouchers.count { !it.state.isFree }
        val notInRecycler = vouchers.count { it.state.isFree && !it.voucher.isInRecycler() }

        coinageLogD(
            "Voucher selection selectable=$selectable of=${vouchers.size}" +
                " claimed=$claimed notInRecycler=$notInRecycler"
        )
    }
}

/** An asset the ledger has never heard of carries no claim, which is the same as a free one. */
private fun Map<OwnAsset, CoinageAssetState>.stateOf(asset: OwnAsset) =
    this[asset] ?: CoinageAssetState.UNTRACKED
