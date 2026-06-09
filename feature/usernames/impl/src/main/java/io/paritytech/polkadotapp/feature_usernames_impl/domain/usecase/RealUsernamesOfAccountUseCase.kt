package io.paritytech.polkadotapp.feature_usernames_impl.domain.usecase

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.flowOf
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_chain_resources_api.data.repository.ResourcesRepository
import io.paritytech.polkadotapp.feature_chain_resources_api.domain.model.ConsumerInfo
import io.paritytech.polkadotapp.feature_usernames_api.data.LocalUsernameStorage
import io.paritytech.polkadotapp.feature_usernames_api.domain.model.StoredUsername
import io.paritytech.polkadotapp.feature_usernames_api.domain.model.Username
import io.paritytech.polkadotapp.feature_usernames_api.domain.usecase.UsernameOfAccountUseCase
import io.paritytech.polkadotapp.feature_usernames_impl.domain.UsernamesChainProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import javax.inject.Inject

class RealUsernamesOfAccountUseCase @Inject constructor(
    private val localUsernameStorage: LocalUsernameStorage,
    private val resourcesRepository: ResourcesRepository,
    private val accountRepository: AccountRepository,
    private val usernamesChainProvider: UsernamesChainProvider
) : UsernameOfAccountUseCase {
    override operator fun invoke(): Flow<StoredUsername?> {
        return accountRepository.areAccountsInitializedFlow()
            .flatMapLatest { accountsInitialized ->
                if (accountsInitialized) {
                    onChain().transformLatest {
                        if (it != null) {
                            emit(it)
                        } else {
                            emitAll(local())
                        }
                    }
                } else {
                    flowOf { null }
                }
            }
    }

    override fun initiallyClaimedLightUsername(): Flow<Username?> {
        return localUsernameStorage.valueFlow()
    }

    override suspend fun getUsername(): Result<StoredUsername?> {
        val account = accountRepository.getWalletAccount()
        val chain = usernamesChainProvider.chain()
        val accountId = account.accountIdIn(chain)

        return getOnChainUsername(chain, accountId).map { onChainUsername ->
            onChainUsername ?: getLocallyStoredUsername()
        }
    }

    private fun local(): Flow<StoredUsername?> = localUsernameStorage.valueFlow()
        .map { it?.toStoredLightUsername() }

    private suspend fun getOnChainUsername(chain: Chain, accountId: AccountId): Result<StoredUsername?> {
        return resourcesRepository.consumerInfoLocal(chain.id, accountId)
            .map { it?.toStoredUsername() }
    }

    private suspend fun getLocallyStoredUsername(): StoredUsername? {
        return localUsernameStorage.getValue()?.toStoredLightUsername()
    }

    private suspend fun onChain(): Flow<StoredUsername?> = accountRepository.walletAccountFlow()
        .map { it.accountIdIn(usernamesChainProvider.chain()) }
        .flatMapLatest { resourcesRepository.consumerInfoLocalFlow(usernamesChainProvider.chainId, it) }
        .map { consumerInfo -> consumerInfo?.toStoredUsername() }

    private fun Username.toStoredLightUsername(): StoredUsername {
        return StoredUsername(
            liteUsername = this,
            fullUsername = null,
            isOnChain = false
        )
    }

    private fun ConsumerInfo.toStoredUsername(): StoredUsername {
        return StoredUsername(
            liteUsername = Username.fromFullValue(liteUsername),
            fullUsername = fullUsername?.let(Username::fromFullValue),
            isOnChain = true,
        )
    }
}
