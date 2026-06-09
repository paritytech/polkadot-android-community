package io.paritytech.polkadotapp.feature_videogame_impl.data.updaters

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.network.updaters.SingleStorageKeyUpdater
import io.paritytech.polkadotapp.chains.network.updaters.Updater
import io.paritytech.polkadotapp.chains.storage.StorageCache
import io.paritytech.polkadotapp.chains.util.WithRuntime
import io.paritytech.polkadotapp.feature_account_api.data.CandidateAccount
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.BandersnatchSecretsStorage
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.getAliasInContext
import io.paritytech.polkadotapp.feature_account_api.domain.model.MetaAccount
import io.paritytech.polkadotapp.feature_videogame_impl.data.SCORE
import io.paritytech.polkadotapp.feature_videogame_impl.data.models.onchain.OnChainAccountOrPerson
import io.paritytech.polkadotapp.feature_videogame_impl.data.players
import io.paritytech.polkadotapp.feature_videogame_impl.data.videoGame
import javax.inject.Inject

class GamePlayerAsPersonUpdater @Inject constructor(
    @CandidateAccount scope: Updater.NoChainScope<MetaAccount>,
    chainRegistry: ChainRegistry,
    storageCache: StorageCache,
    private val bandersnatchSecretsStorage: BandersnatchSecretsStorage,
) : SingleStorageKeyUpdater<MetaAccount>(scope, chainRegistry, storageCache) {
    context(WithRuntime)
    override suspend fun storageKey(scopeValue: MetaAccount, chain: Chain): String {
        val scoreAlias = bandersnatchSecretsStorage.getAliasInContext(scopeValue.id, BandersnatchContext.SCORE)

        return runtime.metadata.videoGame
            .players
            .storageKey(OnChainAccountOrPerson.Person(scoreAlias))
    }
}
