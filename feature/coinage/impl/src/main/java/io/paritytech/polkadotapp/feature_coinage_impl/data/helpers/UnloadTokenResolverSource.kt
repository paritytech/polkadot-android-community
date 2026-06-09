package io.paritytech.polkadotapp.feature_coinage_impl.data.helpers

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.chains.multiNetwork.withRuntime
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_account_api.data.repository.getCandidateAccount
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.BandersnatchSecretsStorage
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.getAliasInContext
import io.paritytech.polkadotapp.feature_coinage_impl.data.blockchain.coinage
import io.paritytech.polkadotapp.feature_coinage_impl.data.blockchain.unloadTokenTimePeriodPeopleLitePeople
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import javax.inject.Inject

interface UnloadTokenResolverSource {
    class OnChainConstants(
        val periodDuration: Long,
        val maxCounter: Long
    )

    suspend fun getConstants(chainId: ChainId): OnChainConstants

    suspend fun generateAlias(context: ByteArray): ByteArray
}

class PeopleLiteUnloadTokenResolverSource @Inject constructor(
    private val chainRegistry: ChainRegistry,
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
    private val accountRepository: AccountRepository,
    private val bandersnatchStorage: BandersnatchSecretsStorage,
) : UnloadTokenResolverSource {
    companion object {
        private const val MAX_COUNTER = 10L
    }

    override suspend fun getConstants(chainId: ChainId): UnloadTokenResolverSource.OnChainConstants {
        return chainRegistry.withRuntime(chainId) {
            UnloadTokenResolverSource.OnChainConstants(
                runtime.metadata.coinage.unloadTokenTimePeriodPeopleLitePeople,
                MAX_COUNTER // We have to use pallet view to get this value
            )
        }
    }

    override suspend fun generateAlias(context: ByteArray): ByteArray {
        val metaAccount = accountRepository.getWalletAccount()
        return bandersnatchStorage.getAliasInContext(
            metaAccount.id,
            BandersnatchContext(context)
        ).value
    }
}

class PeopleUnloadTokenResolverSource @Inject constructor(
    private val chainRegistry: ChainRegistry,
    private val bandersnatchStorage: BandersnatchSecretsStorage,
    private val accountRepository: AccountRepository
) : UnloadTokenResolverSource {
    companion object {
        private const val MAX_COUNTER = 10L
    }

    override suspend fun getConstants(chainId: ChainId): UnloadTokenResolverSource.OnChainConstants {
        return chainRegistry.withRuntime(chainId) {
            UnloadTokenResolverSource.OnChainConstants(
                periodDuration = runtime.metadata.coinage.unloadTokenTimePeriodPeopleLitePeople,
                maxCounter = MAX_COUNTER // We have to use pallet view to get this value
            )
        }
    }

    override suspend fun generateAlias(context: ByteArray): ByteArray {
        val metaAccount = accountRepository.getCandidateAccount()
        return bandersnatchStorage.getAliasInContext(
            metaAccount.id,
            BandersnatchContext(context)
        ).value
    }
}
