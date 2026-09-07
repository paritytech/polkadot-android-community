package io.paritytech.polkadotapp.feature_coinage_api.domain.model

import java.math.BigDecimal

data class TransferPlan(
    val strategyType: StrategyType
)

data class PlannedMemoEntry(
    val coinDerivationIndex: Int,
    val valueExponent: ValueExponent
)

/** One coin split into [recipientDenominations] handed to the recipient and [changeDenominations] kept. */
data class CoinSplit(
    val splitFrom: Coin,
    val recipientDenominations: List<ValueExponent>,
    val changeDenominations: List<ValueExponent>
)

sealed interface StrategyType {
    data class ExactCoins(
        val coins: List<Coin>
    ) : StrategyType

    /** Independent splits of distinct coins, plus [exactCoins] handed over as they are. */
    data class Split(
        val splits: List<CoinSplit>,
        val exactCoins: List<Coin>
    ) : StrategyType

    data class UnloadAndSplit(
        val vouchersToUnload: List<RecyclerVoucher>,
        val recipientAmount: BigDecimal,
        val exactCoins: List<Coin>
    ) : StrategyType
}
