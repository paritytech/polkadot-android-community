package io.paritytech.polkadotapp.feature_chats_impl.domain.usecase

import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.EncodedPublicKey
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_account_api.domain.model.SharedSecretDerivationDomain
import io.paritytech.polkadotapp.feature_chain_resources_api.data.repository.ResourcesRepository
import io.paritytech.polkadotapp.feature_chain_resources_api.data.repository.requireConsumerInfo
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ContactOrigins
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ContactsRepository
import io.paritytech.polkadotapp.feature_chats_impl.domain.models.StartChatData
import io.paritytech.polkadotapp.feature_usernames_api.domain.model.Username
import javax.inject.Inject

class StartChatDataUseCase @Inject constructor(
    knownChains: KnownChains,
    private val chainRegistry: ChainRegistry,
    private val resourcesRepository: ResourcesRepository,
    private val accountRepository: AccountRepository,
    private val contactsRepository: ContactsRepository
) {
    private val chainId = knownChains.people

    suspend fun getCurrentAccountId(): AccountId {
        return accountRepository.getWalletAccount().accountIdIn(chainRegistry.getChain(chainId))
    }

    suspend fun getStartChatData(contactAccountId: AccountId): Result<StartChatData> {
        val existingContact = contactsRepository.getContact(contactAccountId)
        if (existingContact != null) {
            return Result.success(StartChatData.ExistingChat(contactAccountId))
        }

        return resourcesRepository
            .requireConsumerInfo(chainRegistry.getChain(chainId), contactAccountId)
            .mapCatching { consumerInfo ->
                val walletAccount = accountRepository.getWalletAccount()

                StartChatData.NewChat(
                    contactAccountId = contactAccountId,
                    username = Username.fromFullValue(consumerInfo.username),
                    avatar = null,
                    chatKey = EncodedPublicKey(consumerInfo.identifierKey.value),
                    sharedSecretDerivationDomain = SharedSecretDerivationDomain.CHAT,
                    ourMetaAccountId = walletAccount.id,
                    origin = ContactOrigins.CONTACT_CHAT
                )
            }
    }
}
