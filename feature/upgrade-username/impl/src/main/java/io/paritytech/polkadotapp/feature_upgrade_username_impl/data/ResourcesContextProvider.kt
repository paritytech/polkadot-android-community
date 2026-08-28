package io.paritytech.polkadotapp.feature_upgrade_username_impl.data

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.withRuntime
import io.paritytech.polkadotapp.feature_chain_resources_api.data.api.networkSuffix
import io.paritytech.polkadotapp.feature_chain_resources_api.data.api.resources
import javax.inject.Inject

class ResourcesContextProvider @Inject constructor(
    private val chainRegistry: ChainRegistry,
) {
    suspend fun context(): BandersnatchContext {
        val networkSuffix = chainRegistry.withRuntime(chainRegistry.peopleChain().id) {
            runtime.metadata.resources.networkSuffix
        } ?: error("Resources.Suffix constant is missing — runtime does not support product contexts")

        return BandersnatchContext.resources(networkSuffix)
    }
}
