package io.paritytech.polkadotapp.feature_chats_impl.domain.sessions.signer

import io.novasama.substrate_sdk_android.encrypt.keypair.Keypair
import io.novasama.substrate_sdk_android.encrypt.keypair.substrate.Sr25519Keypair
import io.novasama.substrate_sdk_android.extensions.toHexString
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.feature_account_api.domain.usecase.AccountDerivationUseCase
import javax.inject.Inject

// No period segment: the slot allocator renews the same per-chat account across days.
class ChatSignerAccountDerivation @Inject constructor(
    private val accountDerivationUseCase: AccountDerivationUseCase,
) {
    suspend fun deriveKeypair(contactAccountId: AccountId): Result<Sr25519Keypair> {
        val contactHex = contactAccountId.value.toHexString(withPrefix = false)
        val derived = accountDerivationUseCase.deriveKeypair("//chat-signer//$contactHex")
        return derived.mapCatching { keypair -> keypair.requireSr25519(contactHex) }
    }

    private fun Keypair.requireSr25519(contactHex: String): Sr25519Keypair {
        return this as? Sr25519Keypair
            ?: error("Chat signer keypair for contact $contactHex must be Sr25519, got ${this::class.simpleName}")
    }
}
