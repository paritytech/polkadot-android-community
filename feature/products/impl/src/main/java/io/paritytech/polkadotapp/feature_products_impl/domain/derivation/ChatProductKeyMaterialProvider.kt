package io.paritytech.polkadotapp.feature_products_impl.domain.derivation

import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_account_api.domain.derivation.DerivationIndex32
import io.paritytech.polkadotapp.feature_account_api.domain.derivation.SharedSecretKeyMaterialProvider
import io.paritytech.polkadotapp.feature_account_api.domain.model.SharedSecretDerivationDomain
import io.paritytech.polkadotapp.feature_account_api.domain.usecase.AccountDerivationUseCase
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTldProvider
import io.paritytech.polkadotapp.feature_dotns_api.domain.getTldRetrying
import io.paritytech.polkadotapp.feature_products_api.domain.deriveEntropy.DeriveEntropyUseCase
import io.paritytech.polkadotapp.feature_products_api.model.derivation.ReservedProductIds
import io.paritytech.polkadotapp.feature_products_api.model.derivation.productAccountPath
import javax.inject.Inject

private val ECDH_ENTROPY_KEY = "ecdh".encodeToByteArray()

/**
 * CHAT_PRODUCT_IDENTITY_DEMO: the chat key is `chat.<tld>` product entropy under key `ecdh`, the same
 * bytes a Chat product gets from `deriveEntropy("ecdh")`. It only matches the published chat identity
 * when the wallet account is `chat.<tld>` too, so a wallet created before the demo is rejected rather
 * than given a key that disagrees with its registration.
 */
class ChatProductKeyMaterialProvider @Inject constructor(
    private val dotNsTldProvider: DotNsTldProvider,
    private val deriveEntropyUseCase: DeriveEntropyUseCase,
    private val accountRepository: AccountRepository,
    private val accountDerivationUseCase: AccountDerivationUseCase,
) : SharedSecretKeyMaterialProvider {
    override val domain: SharedSecretDerivationDomain = SharedSecretDerivationDomain.CHAT

    override suspend fun deriveKeyMaterial(): Result<ByteArray> {
        val chatProductId = ReservedProductIds.chat(dotNsTldProvider.getTldRetrying())
        val chatAccountPath = productAccountPath(chatProductId, DerivationIndex32.default())

        return accountDerivationUseCase.deriveAccount(chatAccountPath)
            .mapCatching { chatAccount ->
                val walletAccount = accountRepository.getWalletAccount()
                check(walletAccount.defaultPubKey().value.contentEquals(chatAccount.value)) {
                    "Wallet is not the $chatProductId account; the chat identity demo needs a wallet created by the demo build"
                }
            }
            .flatMap { deriveEntropyUseCase.deriveEntropy(chatProductId, ECDH_ENTROPY_KEY) }
    }
}
