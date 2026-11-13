package io.paritytech.polkadotapp.feature_chats_impl.domain.interactors

import io.novasama.substrate_sdk_android.extensions.toHexString
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.CurrentTimeContext
import io.paritytech.polkadotapp.common.domain.model.requireX25519PublicKey
import io.paritytech.polkadotapp.common.domain.model.x25519OrNull
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.common.utils.decodeFromByteArrayCatching
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.mapToSet
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_account_api.domain.model.SharedSecretDerivationDomain
import io.paritytech.polkadotapp.feature_chain_resources_api.data.repository.ResourcesRepository
import io.paritytech.polkadotapp.feature_chain_resources_api.domain.model.ConsumerInfo
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.AddContactUseCase
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageOrigin
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatRequest
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatRequestId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatStateForSync
import io.paritytech.polkadotapp.feature_chats_api.domain.model.Contact
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ContactOrigin
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ContactOrigins
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatRequestRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatRoomRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ContactsRepository
import io.paritytech.polkadotapp.feature_chats_impl.domain.ChatEngine
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.newOutgoingChatRequest
import io.paritytech.polkadotapp.feature_chats_transport_protocol.scale.VersionedRequestContent
import io.paritytech.polkadotapp.feature_usernames_api.domain.model.Username
import io.paritytech.polkadotapp.tools_push_notifications_api.PushNotificationsHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Instant

class RealAddContactUseCase @Inject constructor(
    private val contactsRepository: ContactsRepository,
    private val chatRequestRepository: ChatRequestRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val pushNotificationsHelper: PushNotificationsHelper,
    private val chatEngine: ChatEngine,
    private val coroutineDispatchers: CoroutineDispatchers,
    private val resourcesRepository: ResourcesRepository,
    private val accountRepository: AccountRepository,
    private val chainRegistry: ChainRegistry,
    private val knownChains: KnownChains,
    private val currentTimeContext: CurrentTimeContext,
) : AddContactUseCase {
    private companion object {
        const val UNKNOWN_DELIVERY_PERIOD = 0u
    }

    override suspend fun addContactWithChatRequest(
        contactAccountId: AccountId,
        username: Username?,
        avatar: String?,
        chatKey: ByteArray,
        sharedSecretDerivationDomain: SharedSecretDerivationDomain,
        ourMetaAccountId: Long,
        origin: ContactOrigin,
        welcomeMessage: ChatMessage.Content.RichText?,
    ): Result<Unit> = withContext(coroutineDispatchers.io) {
        val token = pushNotificationsHelper.getCurrentToken()

        val contact = Contact(
            accountId = contactAccountId,
            username = username?.getDisplayUsername(),
            chatKey = chatKey.requireX25519PublicKey(),
            sharedSecretDerivationDomain = sharedSecretDerivationDomain,
            ourMetaAccountId = ourMetaAccountId,
            avatarUrl = avatar,
            origin = origin,
            lastSharedPushToken = token,
            addedAt = currentTimeContext.currentTime(),
        )

        Timber.i("addContact: recording chat request to ${contact.accountId} for background delivery")
        createPendingContactChat(contact, newOutgoingChatRequest(contact.accountId), welcomeMessage)
    }

    override suspend fun addAlreadyEstablishedContactsById(accountIds: List<AccountId>): Result<Unit> {
        return saveContactsResolvedOnChain(accountIds) { consumerInfo, ourMetaAccountId, now ->
            val contact = consumerInfo.toContact(ourMetaAccountId, addedAt = now, establishedAt = now, pendingChatRequestId = null)
            contactsRepository.saveContact(contact)
            chatRoomRepository.createRoomIfNotExists(ChatId.fromContact(contact.accountId))
        }
    }

    suspend fun addContactsWithPendingRequests(requests: List<ChatStateForSync.OutgoingRequest>): Result<Unit> {
        val requestsByPeer = requests.filter { contactsRepository.getContact(it.peer) == null }.associateBy { it.peer }

        return saveContactsResolvedOnChain(requestsByPeer.keys.toList()) { consumerInfo, ourMetaAccountId, now ->
            val request = requestsByPeer.getValue(consumerInfo.accountId)
            val contact = consumerInfo.toContact(ourMetaAccountId, addedAt = now, establishedAt = null, pendingChatRequestId = request.id)

            contactsRepository.withTransaction {
                chatRequestRepository.saveFromSync(request.toChatRequest())
                chatRoomRepository.createRoomIfNotExists(ChatId.fromContact(contact.accountId))
                request.toWelcomeMessage()?.let { chatEngine.saveMessage(it) }
                contactsRepository.saveContact(contact)
            }
        }
    }

    override suspend fun getContactsAddedAfter(after: Instant): List<Contact> {
        return contactsRepository.getAddedAfter(after)
    }

    override suspend fun getEstablishedContactsAddedAfter(after: Instant): List<Contact> {
        return contactsRepository.getEstablishedAfter(after)
    }

    override fun subscribeContactAccountIds(): Flow<Set<AccountId>> {
        return contactsRepository.subscribeContacts()
            .map { contacts -> contacts.mapToSet { it.accountId } }
    }

    override fun observeContactsChanged(): Flow<Unit> = contactsRepository.observeContactsChanged()

    private suspend fun saveContactsResolvedOnChain(
        accountIds: List<AccountId>,
        save: suspend (consumerInfo: ConsumerInfo, ourMetaAccountId: Long, now: Instant) -> Unit,
    ): Result<Unit> {
        if (accountIds.isEmpty()) return Result.success(Unit)

        val peopleChain = chainRegistry.getChain(knownChains.people)
        return resourcesRepository.resolveConsumers(peopleChain.id, accountIds).flatMap { consumerInfoByAccount ->
            runCatching {
                val walletAccount = accountRepository.getWalletAccount()
                val now = currentTimeContext.currentTime()

                accountIds.forEach { accountId ->
                    val consumerInfo = consumerInfoByAccount[accountId] ?: run {
                        Timber.w("saveContactsResolvedOnChain: no ConsumerInfo for 0x%s", accountId.value.toHexString())
                        return@forEach
                    }
                    runCatching { save(consumerInfo, walletAccount.id, now) }.onFailure {
                        Timber.w(it, "saveContactsResolvedOnChain: save failed for 0x${accountId.value.toHexString()}")
                    }
                }
            }
        }
    }

    private fun ConsumerInfo.toContact(
        ourMetaAccountId: Long,
        addedAt: Instant,
        establishedAt: Instant?,
        pendingChatRequestId: ChatRequestId?,
    ): Contact {
        return Contact(
            accountId = accountId,
            username = username,
            chatKey = identifierKey.x25519OrNull() ?: error("Peer account uses an unsupported chat encryption key type"),
            ourMetaAccountId = ourMetaAccountId,
            avatarUrl = null,
            sharedSecretDerivationDomain = SharedSecretDerivationDomain.CHAT,
            origin = ContactOrigins.CONTACT_CHAT,
            pendingChatRequestId = pendingChatRequestId,
            addedAt = addedAt,
            establishedAt = establishedAt,
        )
    }

    private fun ChatStateForSync.OutgoingRequest.toChatRequest(): ChatRequest {
        return ChatRequest(
            welcomeMessageId = id,
            peer = peer,
            timestamp = timestamp,
            direction = ChatRequest.Direction.OUTGOING,
            status = ChatRequest.Status.PENDING,
            delivery = ChatRequest.Delivery.DeliveredAnonymously(period = UNKNOWN_DELIVERY_PERIOD),
        )
    }

    private fun ChatStateForSync.OutgoingRequest.toWelcomeMessage(): ChatMessage? {
        val content = BinaryScale.decodeFromByteArrayCatching<VersionedRequestContent>(encodedContent)
            .onFailure { Timber.w(it, "Failed to decode the content of synced outgoing request $id") }
            .getOrNull()
        val welcome = when (content) {
            is VersionedRequestContent.V1 -> content.content.welcomeMessage
            is VersionedRequestContent.V2 -> content.content.welcomeMessage
            null -> null
        } ?: return null

        return ChatMessage(
            id = id,
            chatId = ChatId.fromContact(peer),
            timestamp = timestamp,
            content = ChatMessage.Content.ChatRequest(ChatMessage.Content.RichText(text = welcome.text, attachments = emptyList())),
            origin = ChatMessageOrigin.User,
            status = ChatMessage.Status.IS_SENT,
        )
    }

    // The contact link is written last: it is what makes a recorded request visible to background delivery,
    // which reads the welcome message back and sends the request from an unlinkable account.
    private suspend fun createPendingContactChat(
        contact: Contact,
        chatRequest: ChatRequest,
        welcomeMessage: ChatMessage.Content.RichText?,
    ): Result<Unit> = runCancellableCatching {
        contactsRepository.withTransaction {
            chatRequestRepository.save(chatRequest)
            chatRoomRepository.createRoomIfNotExists(ChatId.fromContact(contact.accountId))
            welcomeMessage?.let { saveWelcomeMessage(contact, chatRequest, it) }
            contactsRepository.saveContact(contact.copy(pendingChatRequestId = chatRequest.id))
        }
    }

    private suspend fun saveWelcomeMessage(
        contact: Contact,
        chatRequest: ChatRequest,
        welcomeMessage: ChatMessage.Content.RichText?,
    ) {
        val chatMessage = ChatMessage(
            id = chatRequest.id,
            chatId = ChatId.fromContact(contact.accountId),
            timestamp = chatRequest.timestamp,
            content = ChatMessage.Content.ChatRequest(welcomeMessage),
            origin = ChatMessageOrigin.User,
            status = ChatMessage.Status.PROCESSING,
        )

        chatEngine.saveMessage(chatMessage)
    }
}
