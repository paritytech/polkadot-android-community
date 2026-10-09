package io.paritytech.polkadotapp.feature_chats_impl.domain.sessions.signer

import io.novasama.substrate_sdk_android.encrypt.keypair.substrate.Sr25519Keypair
import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.chains.multiNetwork.connection.ChainConnectionRefCounter
import io.paritytech.polkadotapp.chains.multiNetwork.connection.withConnectionEnabled
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.flatRecover
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.AccountSecretsStorage
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.getMetaAccountSr25519Keypair
import io.paritytech.polkadotapp.feature_chats_api.domain.model.Contact
import io.paritytech.polkadotapp.feature_statement_store_api.domain.slotAllocator.OnExistingAllocationStrategy
import io.paritytech.polkadotapp.feature_statement_store_api.domain.slotAllocator.SlotPriority
import io.paritytech.polkadotapp.feature_statement_store_api.domain.slotAllocator.StatementStoreSlotAllocationError
import io.paritytech.polkadotapp.feature_statement_store_api.domain.slotAllocator.StatementStoreSlotAllocator
import javax.inject.Inject

private const val SIGNER_CONNECTION_TAG = "ContactChatSigner"

// Only a full slot table downgrades to the username signer; any other failure is returned so the caller retries.
class ContactChatSignerResolver @Inject constructor(
    private val accountDerivation: ChatSignerAccountDerivation,
    private val slotAllocator: StatementStoreSlotAllocator,
    private val accountSecretsStorage: AccountSecretsStorage,
    private val chainConnectionRefCounter: ChainConnectionRefCounter,
    private val knownChains: KnownChains,
) {
    suspend fun resolve(contact: Contact): Result<ContactChatSigner> {
        return accountDerivation.deriveKeypair(contact.accountId)
            .flatMap { chatKeypair -> resolveWithChatKeypair(contact, chatKeypair) }
            .logFailure("Failed to resolve chat signer for contact ${contact.username}")
    }

    private suspend fun usernameSigner(contact: Contact): Result<ContactChatSigner> = runCancellableCatching {
        val keypair = accountSecretsStorage.getMetaAccountSr25519Keypair(contact.ourMetaAccountId)
        ContactChatSigner(keypair, ChatSignerKind.USERNAME)
    }

    private suspend fun resolveWithChatKeypair(contact: Contact, chatKeypair: Sr25519Keypair): Result<ContactChatSigner> {
        val chatAccountId = chatKeypair.publicKey.toDataByteArray()
        val privateSigner: Result<ContactChatSigner> = ensureSlot(chatAccountId).map { ContactChatSigner(chatKeypair, ChatSignerKind.PRIVATE) }
        return privateSigner.flatRecover { error -> usernameSignerIfNoSlot(contact, error) }
    }

    private suspend fun ensureSlot(chatAccountId: AccountId): Result<Unit> {
        return slotAllocator.hasCurrentAllocation(chatAccountId).flatMap { hasCurrent ->
            if (hasCurrent) return@flatMap Result.success(Unit)

            allocateSlot(chatAccountId)
        }
    }

    private suspend fun allocateSlot(chatAccountId: AccountId): Result<Unit> {
        return chainConnectionRefCounter.withConnectionEnabled(knownChains.people, SIGNER_CONNECTION_TAG) {
            with(StalenessReportCollector.NoOp) {
                slotAllocator.allocate(chatAccountId, OnExistingAllocationStrategy.IGNORE, SlotPriority.Critical)
            }
        }
    }

    private suspend fun usernameSignerIfNoSlot(contact: Contact, error: Throwable): Result<ContactChatSigner> {
        if (error !is StatementStoreSlotAllocationError.NoAllocationAvailable) return Result.failure(error)

        return usernameSigner(contact)
    }
}
