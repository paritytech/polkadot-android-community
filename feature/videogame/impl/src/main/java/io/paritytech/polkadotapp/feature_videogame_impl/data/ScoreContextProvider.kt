package io.paritytech.polkadotapp.feature_videogame_impl.data

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.withRuntime
import javax.inject.Inject

class ScoreContextProvider @Inject constructor(
    private val chainRegistry: ChainRegistry,
) {
    suspend fun context(): BandersnatchContext {
        val networkSuffix = chainRegistry.withRuntime(chainRegistry.peopleChain().id) {
            runtime.metadata.score.networkSuffix
        } ?: error("Score.Suffix constant is missing — runtime does not support product contexts")

        return BandersnatchContext.score(networkSuffix)
    }
}
