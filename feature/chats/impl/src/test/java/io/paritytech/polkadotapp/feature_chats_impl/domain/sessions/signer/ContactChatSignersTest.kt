package io.paritytech.polkadotapp.feature_chats_impl.domain.sessions.signer

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.novasama.substrate_sdk_android.encrypt.keypair.substrate.Sr25519Keypair
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.feature_chats_api.domain.model.Contact
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ContactsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ContactChatSignersTest {
    private val contactAccountId = ByteArray(32) { 1 }.toDataByteArray()
    private val chatPublicKey = ByteArray(32) { 2 }
    private val chatKeypair: Sr25519Keypair = mockk { every { publicKey } returns chatPublicKey }
    private val usernameKeypair: Sr25519Keypair = mockk()
    private val privateSigner = ContactChatSigner.Private(chatKeypair)
    private val usernameSigner = ContactChatSigner.Username(usernameKeypair)

    private val resolver: ContactChatSignerResolver = mockk()
    private val accountDerivation: ChatSignerAccountDerivation = mockk()
    private val slotAllocator = RecordingSlotAllocator()
    private val contactsRepository: ContactsRepository = mockk()

    @Before
    fun setUp() {
        coEvery { accountDerivation.deriveKeypair(contactAccountId) } returns Result.success(chatKeypair)
        coEvery { resolver.usernameSigner(any()) } returns Result.success(usernameSigner)
    }

    @Test
    fun `concurrent callers resolve an established chat once per process`() = runTest {
        val signers = createSigners()
        val contact = withStoredContact(establishedAt = Instant.fromEpochSeconds(1))
        coEvery { resolver.resolve(contact) } returns Result.success(privateSigner)

        val keypairs = List(3) { async { signers.keypairFor(contactAccountId) } }.awaitAll()

        keypairs.forEach { assertSame(chatKeypair, it) }
        coVerify(exactly = 1) { resolver.resolve(contact) }
    }

    @Test
    fun `transient failure is retried instead of downgrading to username signer`() = runTest {
        val signers = createSigners()
        val contact = withStoredContact(establishedAt = Instant.fromEpochSeconds(1))
        val attempts = listOf(Result.failure<ContactChatSigner>(IllegalStateException()), Result.success(privateSigner))
        coEvery { resolver.resolve(contact) } returnsMany attempts

        val keypair = signers.keypairFor(contactAccountId)

        assertSame(chatKeypair, keypair)
        coVerify(exactly = 0) { resolver.usernameSigner(any()) }
    }

    @Test
    fun `not established chat signs with username account without allocating`() = runTest {
        val signers = createSigners()
        withStoredContact(establishedAt = null)

        val keypair = signers.keypairFor(contactAccountId)

        assertSame(usernameKeypair, keypair)
        coVerify(exactly = 0) { resolver.resolve(any()) }
    }

    @Test
    fun `release stops renewing the chat slot and resolves again on next use`() = runTest {
        val signers = createSigners()
        val contact = withStoredContact(establishedAt = Instant.fromEpochSeconds(1))
        coEvery { resolver.resolve(contact) } returns Result.success(privateSigner)

        signers.keypairFor(contactAccountId)
        signers.release(contactAccountId)
        signers.keypairFor(contactAccountId)

        assertEquals(listOf(chatPublicKey.toDataByteArray()), slotAllocator.deallocatedTargets)
        coVerify(exactly = 2) { resolver.resolve(contact) }
    }

    private fun TestScope.createSigners(): RealContactChatSigners {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val dispatchers: CoroutineDispatchers = mockk { every { io } returns testDispatcher }
        return RealContactChatSigners(resolver, accountDerivation, slotAllocator, contactsRepository, dispatchers)
    }

    private fun withStoredContact(establishedAt: Instant?): Contact {
        val contact: Contact = mockk {
            every { accountId } returns contactAccountId
            every { username } returns "alice"
            every { this@mockk.establishedAt } returns establishedAt
        }
        coEvery { contactsRepository.getContact(contactAccountId) } returns contact
        return contact
    }
}
