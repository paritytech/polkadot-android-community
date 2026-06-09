package io.paritytech.polkadotapp.feature_products_impl.domain

import io.novasama.substrate_sdk_android.encrypt.EncryptionType
import io.novasama.substrate_sdk_android.encrypt.MultiChainEncryption
import io.novasama.substrate_sdk_android.encrypt.keypair.substrate.Sr25519Keypair
import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.bandersnatch_crypto.ContextualAlias
import io.paritytech.polkadotapp.bandersnatch_crypto.intoBandersnatchContext
import io.paritytech.polkadotapp.common.domain.model.EncodedPublicKey
import io.paritytech.polkadotapp.common.utils.blake2b256
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_account_api.data.repository.getCandidateAccount
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.BandersnatchSecretsStorage
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.getContextualAlias
import io.paritytech.polkadotapp.feature_account_api.domain.usecase.AccountDerivationUseCase
import io.paritytech.polkadotapp.feature_products_api.domain.ProductAccountIdProvider
import io.paritytech.polkadotapp.feature_products_api.model.ProductAccountId
import io.paritytech.polkadotapp.feature_transactions.api.domain.model.TransactionSignerSource
import javax.inject.Inject

class ProductAccountDerivationUseCase @Inject constructor(
    private val accountDerivationUseCase: AccountDerivationUseCase,
    private val bandersnatchSecretsStorage: BandersnatchSecretsStorage,
    private val accountRepository: AccountRepository,
) : ProductAccountIdProvider {
    override suspend fun deriveAccountId(productAccountId: ProductAccountId): Result<EncodedPublicKey> {
        return accountDerivationUseCase.deriveAccount(productAccountId.derivationPath())
    }

    suspend fun deriveTransactionSignerSource(productAccountId: ProductAccountId): Result<TransactionSignerSource.Signed> {
        return deriveKeypair(productAccountId).map { keypair ->
            val encryption = MultiChainEncryption.Substrate(EncryptionType.SR25519)
            TransactionSignerSource.FromKeyPair(keypair, encryption)
        }
    }

    suspend fun deriveKeypair(productAccountId: ProductAccountId): Result<Sr25519Keypair> {
        return accountDerivationUseCase.deriveKeypair(productAccountId.derivationPath())
            .map { it as Sr25519Keypair }
    }

    suspend fun deriveContextualAlias(productAccountId: ProductAccountId): Result<ContextualAlias> {
        return runCatching {
            val context = productAccountId.bandersnatchContext()

            val metaAccount = accountRepository.getCandidateAccount()
            bandersnatchSecretsStorage.getContextualAlias(metaAccount.id, context)
        }
    }

    private fun ProductAccountId.bandersnatchContext(): BandersnatchContext {
        return derivationPath().encodeToByteArray()
            .blake2b256()
            .intoBandersnatchContext()
    }

    private fun ProductAccountId.derivationPath(): String {
        return "/product/$productId/$derivationIndex"
    }
}
