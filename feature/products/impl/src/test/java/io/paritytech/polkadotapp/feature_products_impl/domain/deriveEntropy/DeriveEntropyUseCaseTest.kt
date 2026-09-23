package io.paritytech.polkadotapp.feature_products_impl.domain.deriveEntropy

import io.novasama.substrate_sdk_android.encrypt.mnemonic.MnemonicCreator
import io.novasama.substrate_sdk_android.extensions.fromHex
import io.paritytech.polkadotapp.common.domain.model.X25519PrivateKey
import io.paritytech.polkadotapp.common.utils.X25519KeyGenerator
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.AccountSecretsStorage
import io.paritytech.polkadotapp.feature_account_api.domain.model.MetaAccount
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class DeriveEntropyUseCaseTest {
    private val accountRepository: AccountRepository = mock()
    private val accountSecretsStorage: AccountSecretsStorage = mock()
    private val useCase = RealDeriveEntropyUseCase(accountRepository, accountSecretsStorage)

    private val testMetaId = 1L
    private val testRootEntropy = ByteArray(16) { 0xAB.toByte() }

    @Before
    fun setUp() {
        runBlocking {
            val metaAccount: MetaAccount = mock()
            whenever(metaAccount.id).thenReturn(testMetaId)
            whenever(accountRepository.getWalletAccount()).thenReturn(metaAccount)

            val mnemonic = io.novasama.substrate_sdk_android.encrypt.mnemonic.MnemonicCreator.fromEntropy(testRootEntropy)
            whenever(accountSecretsStorage.getMetaAccountPassphrase(testMetaId)).thenReturn(mnemonic)
        }
    }

    @Test
    fun `derives entropy matching reference implementation`() = runBlocking {
        val productId = ProductId.fromStoredValue("test.product.dot")
        val key = "my-key".toByteArray(Charsets.UTF_8)

        val result = useCase.deriveEntropy(productId, key).getOrThrow()

        val expected = "479d5b9ecce19615397c9f160ee95e2f00c579837a5afb111132dd0da5fd472a".fromHex()
        assertArrayEquals(expected, result)
    }

    /**
     * The chat key (chat.<tld> product entropy under `ecdh`), pinned to the vector the Chat SPA checks against.
     * The mnemonic is the public Substrate dev phrase.
     */
    @Test
    fun `chat product entropy under ecdh matches the shared Chat SPA vector`() = runBlocking {
        val devMnemonic = MnemonicCreator.fromWords("bottom drive obey lake curtain smoke basket hold race lonely fit walk")
        whenever(accountSecretsStorage.getMetaAccountPassphrase(testMetaId)).thenReturn(devMnemonic)

        val entropy = useCase.deriveEntropy(ProductId.fromStoredValue("chat.paseo"), "ecdh".toByteArray()).getOrThrow()
        val publicKey = X25519KeyGenerator().createKeyPair(X25519PrivateKey.fromDerivedBytes(entropy)).publicKey

        assertArrayEquals("1a74c2a4206f629ee938b0dea1a42a102a8d410edbb0527f7aa5c19cddaf9554".fromHex(), entropy)
        assertArrayEquals("261089f9ef6cd5e07da99dcb3a2914f1524f35050882f5fc519110f3365a6c0f".fromHex(), publicKey.bytes.value)
    }

    @Test
    fun `different key produces different entropy`() = runBlocking {
        val productId = ProductId.fromStoredValue("test.product.dot")
        val key = "other-key".toByteArray(Charsets.UTF_8)

        val result = useCase.deriveEntropy(productId, key).getOrThrow()

        val expected = "0d576d5d77cb179bf94b85cb1d644b7879315e74d9e69791fb9cbe94df3c7c39".fromHex()
        assertArrayEquals(expected, result)
    }

    @Test
    fun `different product produces different entropy`() = runBlocking {
        val productId = ProductId.fromStoredValue("other.product.dot")
        val key = "my-key".toByteArray(Charsets.UTF_8)

        val result = useCase.deriveEntropy(productId, key).getOrThrow()

        val expected = "e2f25271c106593c2977d5965f52fa1d2227da0fc110d682c8cb8f30b2ba21c8".fromHex()
        assertArrayEquals(expected, result)
    }

    @Test
    fun `same inputs produce same output (determinism)`() = runBlocking {
        val productId = ProductId.fromStoredValue("test.product.dot")
        val key = "my-key".toByteArray(Charsets.UTF_8)

        val result1 = useCase.deriveEntropy(productId, key).getOrThrow()
        val result2 = useCase.deriveEntropy(productId, key).getOrThrow()

        assertArrayEquals(result1, result2)
    }

    @Test
    fun `rejects key longer than 32 bytes`() = runBlocking {
        val productId = ProductId.fromStoredValue("test.product.dot")
        val key = ByteArray(33) { 0x01 }

        val result = useCase.deriveEntropy(productId, key)

        assertTrue(result.isFailure)
    }

    @Test
    fun `output is 32 bytes`() = runBlocking {
        val productId = ProductId.fromStoredValue("test.product.dot")
        val key = "test".toByteArray(Charsets.UTF_8)

        val result = useCase.deriveEntropy(productId, key).getOrThrow()

        assertTrue(result.size == 32)
    }
}
