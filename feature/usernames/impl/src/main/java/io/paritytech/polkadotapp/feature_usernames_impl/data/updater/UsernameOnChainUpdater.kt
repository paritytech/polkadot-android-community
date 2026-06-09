package io.paritytech.polkadotapp.feature_usernames_impl.data.updater

import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.network.updaters.SingleStorageKeyUpdater
import io.paritytech.polkadotapp.chains.network.updaters.Updater
import io.paritytech.polkadotapp.chains.storage.StorageCache
import io.paritytech.polkadotapp.chains.util.WithRuntime
import io.paritytech.polkadotapp.feature_account_api.domain.model.MetaAccount
import io.paritytech.polkadotapp.feature_chain_resources_api.data.api.consumers
import io.paritytech.polkadotapp.feature_chain_resources_api.data.api.resources
import io.paritytech.polkadotapp.feature_usernames_impl.domain.UsernamesChainProvider

class UsernameOnChainUpdater(
    private val usernamesChainProvider: UsernamesChainProvider,
    chainRegistry: ChainRegistry,
    storageCache: StorageCache,
    scope: Updater.NoChainScope<MetaAccount>,
) : SingleStorageKeyUpdater<MetaAccount>(scope, chainRegistry, storageCache) {
    context(WithRuntime)
    override suspend fun storageKey(scopeValue: MetaAccount, chain: Chain): String {
        val accountId = scopeValue.accountIdIn(usernamesChainProvider.chain())

        return runtime.metadata.resources.consumers.storageKey(accountId)
    }
}
