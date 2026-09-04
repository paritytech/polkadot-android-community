package io.paritytech.polkadotapp.feature_coinage_impl.domain.recycling

import io.paritytech.polkadotapp.common.data.memory.SingleValueCache
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.CoinageBalanceConversionContext
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinRecyclingState
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclingVerdicts
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ageOrNull
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.BalanceEvaluationMode
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.CoinRecyclingStrategy
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.RecyclingSnapshot
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository

/**
 * Forces a coin the chain is about to stop accepting into the recycler, whatever [inner] decided.
 *
 * A chain limit is not a preference, so it wraps the policy instead of being restated inside every set of
 * parameters — and it bypasses the budget, which is what lets the minimum-privacy strategy hold a budget of
 * zero without ever stranding a coin.
 *
 * Being outermost, it is also the whole policy an [BalanceEvaluationMode.IMMEDIATE] pass gets: the forced
 * age is read from local config, so this decorator alone can answer without touching the chain.
 */
class EnsureChainLimitsStrategy(
    private val inner: CoinRecyclingStrategy,
    coinRepository: CoinRepository,
) : CoinRecyclingStrategy by inner {
    private val forcedRecyclingAge = SingleValueCache { coinRepository.getCoinRecyclingAge() }

    context(conversion: CoinageBalanceConversionContext)
    override suspend fun evaluate(
        coins: List<Coin>,
        snapshot: RecyclingSnapshot,
        mode: BalanceEvaluationMode,
    ): RecyclingVerdicts {
        // The delegates read the chain so for IMMEDIATE we don't ask them to guarantee IMMEDIATE on our side
        val verdicts = when (mode) {
            BalanceEvaluationMode.IMMEDIATE -> emptyMap()
            BalanceEvaluationMode.COMPLETE -> inner.evaluate(coins, snapshot, mode)
        }

        val forcedAge = forcedRecyclingAge()

        return coins.associate { coin ->
            val age = coin.ageOrNull()

            val verdict = when {
                age != null && age >= forcedAge -> CoinRecyclingState.MUST_RECYCLE
                else -> verdicts[coin.derivationIndex] ?: CoinRecyclingState.ALLOW_USE
            }

            coin.derivationIndex to verdict
        }
    }
}
