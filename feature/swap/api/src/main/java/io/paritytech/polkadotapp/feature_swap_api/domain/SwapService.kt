package io.paritytech.polkadotapp.feature_swap_api.domain

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.FullChainAssetId
import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.feature_swap_api.domain.model.ReQuoteTrigger
import io.paritytech.polkadotapp.feature_swap_api.domain.model.SwapFee
import io.paritytech.polkadotapp.feature_swap_api.domain.model.SwapFeeArgs
import io.paritytech.polkadotapp.feature_swap_api.domain.model.SwapProgress
import io.paritytech.polkadotapp.feature_swap_api.domain.model.SwapQuote
import io.paritytech.polkadotapp.feature_swap_api.domain.model.SwapQuoteArgs
import kotlinx.coroutines.flow.Flow

interface SwapService {
    context(ComputationalScope)
    fun initiateWarmUp()

    context(ComputationalScope)
    suspend fun sync()

    context(ComputationalScope)
    suspend fun assetsAvailableForSwap(): Flow<Set<FullChainAssetId>>

    context(ComputationalScope)
    suspend fun awaitFullyLoadedRouting()

    context(ComputationalScope)
    suspend fun availableSwapDirectionsFor(asset: Chain.Asset): Flow<Set<FullChainAssetId>>

    context(ComputationalScope)
    suspend fun hasAvailableSwapDirections(asset: Chain.Asset): Flow<Boolean>

    context(ComputationalScope)
    suspend fun quote(
        args: SwapQuoteArgs,
    ): Result<SwapQuote>
    context(ComputationalScope)
    suspend fun estimateFee(feeArgs: SwapFeeArgs): Result<SwapFee>

    context(ComputationalScope)
    suspend fun swap(
        calculatedFee: SwapFee
    ): Flow<SwapProgress>

    context(ComputationalScope)
    fun runSubscriptions(): Flow<ReQuoteTrigger>
}
