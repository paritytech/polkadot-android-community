package io.paritytech.polkadotapp.feature_account_impl.domain.usecase

import io.paritytech.polkadotapp.common.domain.model.X25519KeyPair
import io.paritytech.polkadotapp.common.domain.model.X25519PrivateKey
import io.paritytech.polkadotapp.common.utils.X25519KeyGenerator
import io.paritytech.polkadotapp.common.utils.blake2b256
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.AccountSecretsStorage
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.requireMetaAccountPassphrase
import io.paritytech.polkadotapp.feature_account_api.domain.derivation.SharedSecretKeyMaterialProvider
import io.paritytech.polkadotapp.feature_account_api.domain.derivation.deriveKeyedEntropy
import io.paritytech.polkadotapp.feature_account_api.domain.model.SharedSecretDerivationDomain
import io.paritytech.polkadotapp.feature_account_api.domain.usecase.SharedSecretDerivationUseCase
import javax.inject.Inject

private val ECDH_ROOT_KEY = "ecdh".encodeToByteArray()

class RealSharedSecretDerivationUseCase @Inject constructor(
    private val accountSecretsStorage: AccountSecretsStorage,
    private val keyGenerator: X25519KeyGenerator,
    private val accountRepository: AccountRepository,
    private val keyMaterialProviders: Set<@JvmSuppressWildcards SharedSecretKeyMaterialProvider>,
) : SharedSecretDerivationUseCase {
    override suspend fun deriveForDomain(domain: SharedSecretDerivationDomain): X25519KeyPair {
        val provider = keyMaterialProviders.firstOrNull { it.domain == domain }
        // deriveForDomain is a throwing contract; a contributed scheme that fails must not fall back to
        // the ecdh tree, since that would silently publish and use a key of the other scheme.
        val keyMaterial = provider?.deriveKeyMaterial()?.getOrThrow() ?: deriveFromEcdhTree(domain)

        return keyGenerator.createKeyPair(X25519PrivateKey.fromDerivedBytes(keyMaterial))
    }

    override suspend fun generateOneTimeUse(): X25519KeyPair {
        return keyGenerator.generateRandomKeypair()
    }

    // RFC-0022: the ECDH tree hangs off the account entropy directly, not off an sr25519 derivation.
    // RFC-0004 keeps that derivation and only swaps the curve the material is interpreted under.
    private suspend fun deriveFromEcdhTree(domain: SharedSecretDerivationDomain): ByteArray {
        // We only have single entropy in the app, so we can take it from any account
        val account = accountRepository.getWalletAccount()
        val mnemonic = accountSecretsStorage.requireMetaAccountPassphrase(account.id)

        val root = mnemonic.entropy.blake2b256(key = ECDH_ROOT_KEY)
        return deriveKeyedEntropy(root, ecdhPath(domain))
    }

    private fun ecdhPath(domain: SharedSecretDerivationDomain): String = "//${domain.domain}"
}
