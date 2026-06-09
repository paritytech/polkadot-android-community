package io.paritytech.polkadotapp.feature_usernames_impl.domain.usecase

import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_account_api.data.repository.getWalletAccountIdIn
import io.paritytech.polkadotapp.feature_chain_resources_api.data.repository.ResourcesRepository
import io.paritytech.polkadotapp.feature_usernames_api.data.LocalUsernameStorage
import io.paritytech.polkadotapp.feature_usernames_api.domain.model.Username
import io.paritytech.polkadotapp.feature_usernames_api.domain.usecase.RecoverUsernameUseCase
import io.paritytech.polkadotapp.feature_usernames_impl.domain.UsernamesChainProvider
import javax.inject.Inject

class RealRecoverUsernameUseCase @Inject constructor(
    private val usernamesChainProvider: UsernamesChainProvider,
    private val localUsernameStorage: LocalUsernameStorage,
    private val resourcesRepository: ResourcesRepository,
    private val accountRepository: AccountRepository,
) : RecoverUsernameUseCase {
    override suspend fun invoke(): Result<Boolean> {
        val chain = usernamesChainProvider.chain()
        val accountId = accountRepository.getWalletAccountIdIn(chain)

        return resourcesRepository.consumerInfo(usernamesChainProvider.chainId, accountId)
            .map {
                val usernameFound = it != null
                if (usernameFound) {
                    localUsernameStorage.saveValue(
                        Username.fromFullValue(it.username)
                    )
                }
                usernameFound
            }
    }
}
