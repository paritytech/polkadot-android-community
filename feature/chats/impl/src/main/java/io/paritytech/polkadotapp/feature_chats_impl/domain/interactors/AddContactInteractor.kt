package io.paritytech.polkadotapp.feature_chats_impl.domain.interactors

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.mapList
import io.paritytech.polkadotapp.feature_chats_impl.domain.models.ContactSearchResult
import io.paritytech.polkadotapp.feature_chats_impl.domain.models.StartChatData
import io.paritytech.polkadotapp.feature_chats_impl.domain.usecase.StartChatDataUseCase
import io.paritytech.polkadotapp.feature_usernames_api.domain.usecase.SearchUsernamesUseCase
import javax.inject.Inject

interface AddContactInteractor {
    suspend fun getCurrentAccountId(): AccountId
    suspend fun searchContacts(query: String): Result<List<ContactSearchResult>>
    suspend fun getStartChatData(contactAccountId: AccountId): Result<StartChatData>
}

class RealAddContactInteractor @Inject constructor(
    private val searchUsernamesUseCase: SearchUsernamesUseCase,
    private val startChatDataUseCase: StartChatDataUseCase,
) : AddContactInteractor {
    override suspend fun searchContacts(query: String): Result<List<ContactSearchResult>> {
        return searchUsernamesUseCase(query)
            .map { list ->
                list.distinctBy { it.accountId }
            }
            .mapList {
                ContactSearchResult(
                    accountId = it.accountId,
                    username = it.username
                )
            }
    }

    override suspend fun getCurrentAccountId(): AccountId {
        return startChatDataUseCase.getCurrentAccountId()
    }

    override suspend fun getStartChatData(contactAccountId: AccountId): Result<StartChatData> {
        return startChatDataUseCase.getStartChatData(contactAccountId)
    }
}
