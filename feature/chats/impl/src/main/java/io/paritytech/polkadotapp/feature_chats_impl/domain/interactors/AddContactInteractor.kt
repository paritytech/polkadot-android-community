package io.paritytech.polkadotapp.feature_chats_impl.domain.interactors

import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.mapList
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_account_api.data.repository.getWalletAccountIdIn
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatSearchRecentsRepository
import io.paritytech.polkadotapp.feature_chats_impl.domain.models.Chat
import io.paritytech.polkadotapp.feature_chats_impl.domain.models.ContactSearchResult
import io.paritytech.polkadotapp.feature_chats_impl.domain.models.RecentChat
import io.paritytech.polkadotapp.feature_chats_impl.domain.models.StartChatData
import io.paritytech.polkadotapp.feature_chats_impl.domain.usecase.StartChatDataUseCase
import io.paritytech.polkadotapp.feature_chats_impl.domain.usecase.SubscribeActiveChatsUseCase
import io.paritytech.polkadotapp.feature_usernames_api.domain.usecase.SearchUsernamesUseCase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface AddContactInteractor {
    suspend fun searchContacts(query: String): Result<List<ContactSearchResult>>
    suspend fun getStartChatData(contactAccountId: AccountId): Result<StartChatData>

    context(scope: ComputationalScope)
    fun subscribeChats(): Flow<List<Chat>>

    fun observeRecents(): Flow<List<RecentChat>>
    suspend fun addRecent(chatId: ChatId)
}

class RealAddContactInteractor @Inject constructor(
    private val knownChains: KnownChains,
    private val chainRegistry: ChainRegistry,
    private val accountRepository: AccountRepository,
    private val searchUsernamesUseCase: SearchUsernamesUseCase,
    private val startChatDataUseCase: StartChatDataUseCase,
    private val subscribeActiveChats: SubscribeActiveChatsUseCase,
    private val chatSearchRecentsRepository: ChatSearchRecentsRepository,
) : AddContactInteractor {
    override suspend fun searchContacts(query: String): Result<List<ContactSearchResult>> {
        val ownAccountId = accountRepository.getWalletAccountIdIn(chainRegistry.getChain(knownChains.people))
        return searchUsernamesUseCase(query)
            .map { list ->
                list.distinctBy { it.accountId }
                    .filterNot { it.accountId == ownAccountId }
            }
            .mapList {
                ContactSearchResult(
                    accountId = it.accountId,
                    username = it.username
                )
            }
    }

    override suspend fun getStartChatData(contactAccountId: AccountId): Result<StartChatData> {
        return startChatDataUseCase(contactAccountId)
    }

    context(scope: ComputationalScope)
    override fun subscribeChats(): Flow<List<Chat>> = subscribeActiveChats()

    override fun observeRecents(): Flow<List<RecentChat>> {
        return chatSearchRecentsRepository.observeRecents()
    }

    override suspend fun addRecent(chatId: ChatId) {
        chatSearchRecentsRepository.addRecent(chatId)
    }
}
