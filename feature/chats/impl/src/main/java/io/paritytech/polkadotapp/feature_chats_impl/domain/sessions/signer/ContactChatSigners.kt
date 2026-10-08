package io.paritytech.polkadotapp.feature_chats_impl.domain.sessions.signer

import io.novasama.substrate_sdk_android.encrypt.keypair.substrate.Sr25519Keypair
import io.novasama.substrate_sdk_android.extensions.toHexString
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.common.utils.exponentialRetryDelay
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.retryUntilSuccess
import io.paritytech.polkadotapp.feature_chats_api.domain.model.Contact
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ContactsRepository
import io.paritytech.polkadotapp.feature_statement_store_api.domain.slotAllocator.StatementStoreSlotAllocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// Resolved at most once per process so a chat never flips between private and username signer while the app runs.
interface ContactChatSigners {
    // Chats that are not established yet sign with the username account and never allocate a slot.
    suspend fun keypairFor(contactAccountId: AccountId): Sr25519Keypair

    fun warmUp(contactAccountId: AccountId)

    fun kindFlow(contactAccountId: AccountId): Flow<ChatSignerKind?>

    suspend fun release(contactAccountId: AccountId): Result<Unit>
}

@Singleton
class RealContactChatSigners @Inject constructor(
    private val resolver: ContactChatSignerResolver,
    private val accountDerivation: ChatSignerAccountDerivation,
    private val slotAllocator: StatementStoreSlotAllocator,
    private val contactsRepository: ContactsRepository,
    dispatchers: CoroutineDispatchers,
) : ContactChatSigners, CoroutineScope {
    override val coroutineContext = dispatchers.io + SupervisorJob()

    private val retryDelay = exponentialRetryDelay(initialDelay = 2.seconds, maxDelay = 5.minutes)
    private val resolutions = ConcurrentHashMap<AccountId, Deferred<ContactChatSigner>>()
    private val resolvedKinds = MutableStateFlow<Map<AccountId, ChatSignerKind>>(emptyMap())

    override suspend fun keypairFor(contactAccountId: AccountId): Sr25519Keypair {
        val contactHex = contactAccountId.value.toHexString()
        val contact = contactsRepository.getContact(contactAccountId)
            ?: error("No contact $contactHex to sign chat statements for, expected an existing contact")
        val signer = signerFor(contact)
        return signer.keypair
    }

    override fun warmUp(contactAccountId: AccountId) {
        launch { warmUpIfEstablished(contactAccountId) }
    }

    override fun kindFlow(contactAccountId: AccountId): Flow<ChatSignerKind?> {
        return resolvedKinds
            .map { kinds -> kinds[contactAccountId] }
            .distinctUntilChanged()
    }

    override suspend fun release(contactAccountId: AccountId): Result<Unit> {
        forget(contactAccountId)
        return accountDerivation.deriveKeypair(contactAccountId).flatMap { chatKeypair ->
            val chatAccountId = chatKeypair.publicKey.toDataByteArray()
            slotAllocator.deallocateAllSlots(chatAccountId)
        }
    }

    private suspend fun warmUpIfEstablished(contactAccountId: AccountId) {
        val contact = contactsRepository.getContact(contactAccountId) ?: return
        if (contact.establishedAt == null) return

        resolutionFor(contact)
    }

    private suspend fun signerFor(contact: Contact): ContactChatSigner {
        if (contact.establishedAt == null) return usernameSignerUntilResolved(contact)

        return resolutionFor(contact).await()
    }

    // Cancelled before deallocating so an in-flight allocation cannot re-insert a slot row after release.
    private suspend fun forget(contactAccountId: AccountId) {
        resolutions.remove(contactAccountId)?.cancelAndJoin()
        resolvedKinds.update { kinds -> kinds - contactAccountId }
    }

    private fun resolutionFor(contact: Contact): Deferred<ContactChatSigner> {
        return resolutions.computeIfAbsent(contact.accountId) {
            async { resolveUntilSuccess(contact) }
        }
    }

    private suspend fun resolveUntilSuccess(contact: Contact): ContactChatSigner {
        val signer = retryUntilSuccess(retryDelay) { resolver.resolve(contact) }
        resolvedKinds.update { kinds -> kinds + (contact.accountId to signer.kind) }
        Timber.i("ContactChatSigners: chat with ${contact.username} resolved to ${signer.kind}")
        return signer
    }

    private suspend fun usernameSignerUntilResolved(contact: Contact): ContactChatSigner {
        return retryUntilSuccess(retryDelay) { resolver.usernameSigner(contact) }
    }
}
