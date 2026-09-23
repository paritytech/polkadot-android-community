package io.paritytech.polkadotapp.feature_products_impl.domain.derivation

import io.novasama.substrate_sdk_android.encrypt.keypair.Keypair
import io.novasama.substrate_sdk_android.encrypt.mnemonic.MnemonicCreator
import io.novasama.substrate_sdk_android.extensions.fromHex
import io.paritytech.polkadotapp.common.domain.model.EncodedPublicKey
import io.paritytech.polkadotapp.common.domain.model.X25519PrivateKey
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.X25519KeyGenerator
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.AccountSecretsStorage
import io.paritytech.polkadotapp.feature_account_api.domain.model.MetaAccount
import io.paritytech.polkadotapp.feature_account_api.domain.usecase.AccountDerivationUseCase
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTld
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTldProvider
import io.paritytech.polkadotapp.feature_products_impl.domain.deriveEntropy.RealDeriveEntropyUseCase
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * Pins the public vectors shared with the Chat SPA. The mnemonic is the public Substrate dev phrase.
 * The sr25519 account id cannot be derived on the JVM, so it is supplied by [FakeAccountDerivation].
 */
class ChatProductKeyMaterialProviderTest {
    private val accountRepository: AccountRepository = mock()
    private val accountSecretsStorage: AccountSecretsStorage = mock()

    @Test
    fun `chat key is chat product entropy under ecdh and matches the shared vector`() = runBlocking {
        val provider = provider(walletAccountId = CHAT_PASEO_ACCOUNT_ID, chatAccountId = CHAT_PASEO_ACCOUNT_ID)

        val keyMaterial = provider.deriveKeyMaterial().getOrThrow()
        val keyPair = X25519KeyGenerator().createKeyPair(X25519PrivateKey.fromDerivedBytes(keyMaterial))

        assertArrayEquals(CHAT_PASEO_ECDH_ENTROPY, keyMaterial)
        assertArrayEquals(CHAT_PASEO_X25519_PUBLIC, keyPair.publicKey.bytes.value)
    }

    @Test
    fun `derives along the chat product account path`() = runBlocking {
        val derivation = FakeAccountDerivation(CHAT_PASEO_ACCOUNT_ID)
        provider(walletAccountId = CHAT_PASEO_ACCOUNT_ID, derivation = derivation).deriveKeyMaterial().getOrThrow()

        assertEquals("//product//chat.paseo/0x$INDEX32_ZERO", derivation.requestedPath)
    }

    @Test
    fun `a wallet that is not the chat product account is rejected`() = runBlocking {
        val provider = provider(walletAccountId = UID_PASEO_ACCOUNT_ID, chatAccountId = CHAT_PASEO_ACCOUNT_ID)

        assertTrue(provider.deriveKeyMaterial().isFailure)
    }

    private suspend fun provider(
        walletAccountId: ByteArray,
        chatAccountId: ByteArray = walletAccountId,
        derivation: FakeAccountDerivation = FakeAccountDerivation(chatAccountId),
    ): ChatProductKeyMaterialProvider {
        val wallet: MetaAccount = mock()
        whenever(wallet.id).thenReturn(WALLET_META_ID)
        whenever(wallet.defaultPubKey()).thenReturn(walletAccountId.toDataByteArray())
        whenever(accountRepository.getWalletAccount()).thenReturn(wallet)
        whenever(accountSecretsStorage.getMetaAccountPassphrase(WALLET_META_ID)).thenReturn(MnemonicCreator.fromWords(DEV_MNEMONIC))

        return ChatProductKeyMaterialProvider(
            dotNsTldProvider = FixedTldProvider(DotNsTld.parse("paseo")!!),
            deriveEntropyUseCase = RealDeriveEntropyUseCase(accountRepository, accountSecretsStorage),
            accountRepository = accountRepository,
            accountDerivationUseCase = derivation,
        )
    }

    private class FixedTldProvider(private val tld: DotNsTld) : DotNsTldProvider {
        override fun currentTldOrNull(): DotNsTld = tld

        override suspend fun getTld(): Result<DotNsTld> = Result.success(tld)
    }

    private class FakeAccountDerivation(private val accountId: ByteArray) : AccountDerivationUseCase {
        var requestedPath: String? = null

        override suspend fun deriveAccount(derivationPath: String): Result<EncodedPublicKey> {
            requestedPath = derivationPath
            return Result.success(accountId.toDataByteArray())
        }

        override suspend fun deriveRootAccount(): Result<EncodedPublicKey> = error("not used")

        override suspend fun deriveKeypair(derivationPath: String): Result<Keypair> = error("not used")
    }

    private companion object {
        const val WALLET_META_ID = 1L
        const val DEV_MNEMONIC = "bottom drive obey lake curtain smoke basket hold race lonely fit walk"
        const val INDEX32_ZERO = "0000000012e86013736c5498f050b03cdc16957dff0e422fb92ca77ec3ab168f"

        // Computed with @polkadot/util-crypto for //product//chat.paseo/<index32(0)> and //product//uid.paseo/<index32(0)>.
        val CHAT_PASEO_ACCOUNT_ID = "0xeee691336d6d6989d9d2fc1306c672c31b902ec2f47a06a14ed3ec05325b707e".fromHex()
        val UID_PASEO_ACCOUNT_ID = "0xbc32012e0f975f0015b731a6a8f6a9da9212b8fbc5a973a9f89511fd0d6bac7c".fromHex()

        val CHAT_PASEO_ECDH_ENTROPY = "0x1a74c2a4206f629ee938b0dea1a42a102a8d410edbb0527f7aa5c19cddaf9554".fromHex()
        val CHAT_PASEO_X25519_PUBLIC = "0x261089f9ef6cd5e07da99dcb3a2914f1524f35050882f5fc519110f3365a6c0f".fromHex()
    }
}
