package io.paritytech.polkadotapp.feature_chats_impl.domain.sessions.signer

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.novasama.substrate_sdk_android.encrypt.keypair.substrate.Sr25519Keypair
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.feature_chats_api.domain.model.Contact
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

@OptIn(ExperimentalCoroutinesApi::class)
class ContactChatSignersTest {
    private val contactAccountId = ByteArray(32) { 1 }.toDataByteArray()
    private val chatPublicKey = ByteArray(32) { 2 }
    private val chatKeypair: Sr25519Keypair = mockk { every { publicKey } returns chatPublicKey }
    private val privateSigner = ContactChatSigner(chatKeypair, ChatSignerKind.PRIVATE)

    private val resolver: ContactChatSignerResolver = mockk()
    private val accountDerivation: ChatSignerAccountDerivation = mockk()
    private val slotAllocator = RecordingSlotAllocator()

    @Before
    fun setUp() {
        coEvery { accountDerivation.deriveKeypair(contactAccountId) } returns Result.success(chatKeypair)
    }

    @Test
    fun `concurrent callers resolve an established chat once per process`() = runTest {
        val signers = createSigners()
        val contact = contact()
        coEvery { resolver.resolve(contact) } returns Result.success(privateSigner)

        val keypairs = List(3) { async { signers.keypairFor(contact) } }.awaitAll()

        keypairs.forEach { assertSame(chatKeypair, it) }
        coVerify(exactly = 1) { resolver.resolve(contact) }
    }

    @Test
    fun `transient failure is retried instead of downgrading to username signer`() = runTest {
        val signers = createSigners()
        val contact = contact()
        val attempts = listOf(Result.failure<ContactChatSigner>(IllegalStateException()), Result.success(privateSigner))
        coEvery { resolver.resolve(contact) } returnsMany attempts

        val keypair = signers.keypairFor(contact)

        assertSame(chatKeypair, keypair)
    }

    @Test
    fun `release stops renewing the chat slot and resolves again on next use`() = runTest {
        val signers = createSigners()
        val contact = contact()
        coEvery { resolver.resolve(contact) } returns Result.success(privateSigner)

        signers.keypairFor(contact)
        signers.release(contactAccountId)
        signers.keypairFor(contact)

        assertEquals(listOf(chatPublicKey.toDataByteArray()), slotAllocator.deallocatedTargets)
        coVerify(exactly = 2) { resolver.resolve(contact) }
    }

    private fun TestScope.createSigners(): RealContactChatSigners {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val dispatchers: CoroutineDispatchers = mockk { every { io } returns testDispatcher }
        return RealContactChatSigners(resolver, accountDerivation, slotAllocator, dispatchers)
    }

    private fun contact(): Contact = mockk {
        every { accountId } returns contactAccountId
        every { username } returns "alice"
    }
}
