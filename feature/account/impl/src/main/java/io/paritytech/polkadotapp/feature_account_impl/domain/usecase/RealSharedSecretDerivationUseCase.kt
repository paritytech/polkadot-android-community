package io.paritytech.polkadotapp.feature_account_impl.domain.usecase

import io.novasama.substrate_sdk_android.encrypt.EncryptionType
import io.novasama.substrate_sdk_android.hash.Hasher.blake2b256
import io.paritytech.polkadotapp.common.utils.Secp256r1KeyGenerator
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.AccountSecretsFactory
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.AccountSecretsStorage
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.requireMetaAccountPassphrase
import io.paritytech.polkadotapp.feature_account_api.domain.model.SharedSecretDerivationDomain
import io.paritytech.polkadotapp.feature_account_api.domain.usecase.SharedSecretDerivationUseCase
import java.security.KeyPair
import javax.inject.Inject

class RealSharedSecretDerivationUseCase @Inject constructor(
    private val accountSecretsStorage: AccountSecretsStorage,
    private val accountSecretsFactory: AccountSecretsFactory,
    private val keyGenerator: Secp256r1KeyGenerator,
    private val accountRepository: AccountRepository
) : SharedSecretDerivationUseCase {
    override suspend fun deriveForDomain(domain: SharedSecretDerivationDomain): KeyPair {
        // We only have single entropy in the app, so we can take it from any account
        val account = accountRepository.getWalletAccount()
        val mnemonic = accountSecretsStorage.requireMetaAccountPassphrase(account.id)

        val secrets = accountSecretsFactory.create(
            mnemonic = mnemonic,
            encryptionType = EncryptionType.SR25519,
            derivationPath = domain.derivationPath
        )

        val privateKeyHash = secrets.substrateKeyPair.privateKey.blake2b256()

        return keyGenerator.createKeyPair(privateKeyHash)
    }

    override suspend fun generateOneTimeUse(): KeyPair {
        return keyGenerator.generateRandomKeypair()
    }
}
