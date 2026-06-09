package io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.strategies

import io.paritytech.polkadotapp.feature_coinage_api.domain.model.PlannedMemoEntry
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.StrategyType
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.CoinageTransaction
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.runInTransaction
import javax.inject.Inject

class ExactMatchStrategyFactory @Inject constructor(
    private val coinageTransactionFactory: CoinageTransaction.Factory
) {
    fun create(payload: StrategyType.ExactCoins): ExactMatchStrategy {
        return ExactMatchStrategy(payload, coinageTransactionFactory)
    }
}

class ExactMatchStrategy(
    payload: StrategyType.ExactCoins,
    private val coinageTransactionFactory: CoinageTransaction.Factory
) : TransferStrategy {
    private val coins = payload.coins

    override suspend fun run(): Result<List<PlannedMemoEntry>> {
        // No extrinsic of ours: the coins are handed to the recipient directly and the transaction concludes here.
        // The hand-off leaves them SPENT_LOCALLY; on-chain reconciliation advances them once the recipient claims.
        return coinageTransactionFactory.runInTransaction {
            handOffCoins(coins)
            coins.toMemoEntries()
        }
    }
}
