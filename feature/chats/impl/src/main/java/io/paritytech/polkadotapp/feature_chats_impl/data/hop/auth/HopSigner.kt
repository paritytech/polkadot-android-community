package io.paritytech.polkadotapp.feature_chats_impl.data.hop.auth

import io.novasama.substrate_sdk_android.encrypt.keypair.substrate.Sr25519Keypair
import io.paritytech.polkadotapp.chains.util.sign
import io.paritytech.polkadotapp.chains.util.signing.MessageSigningContext
import io.paritytech.polkadotapp.common.domain.model.intoAccountId
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.feature_account_api.domain.usecase.AccountDerivationUseCase
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.model.HopMultiSignature
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.model.HopMultiSigner
import io.paritytech.polkadotapp.feature_transaction_storage_api.domain.slotAllocator.OnExistingAllocationStrategy
import io.paritytech.polkadotapp.feature_transaction_storage_api.domain.slotAllocator.TransactionStorageSlotAllocator
import javax.inject.Inject

class HopSigner @Inject constructor(
    private val accountDerivation: AccountDerivationUseCase,
    private val slotAllocator: TransactionStorageSlotAllocator
) {
    suspend fun multiSigner(): HopMultiSigner = HopMultiSigner.SR25519(deriveKeypairAndAllocate().publicKey)

    suspend fun sign(payload: ByteArray): HopMultiSignature {
        val keypair = deriveKeypairAndAllocate()
        val rawSignature = keypair.sign(payload, MessageSigningContext.trustedContent())
        return HopMultiSignature.SR25519(rawSignature)
    }

    private suspend fun deriveKeypairAndAllocate(): Sr25519Keypair {
        return accountDerivation.deriveKeypair(DERIVATION_PATH)
            .mapCatching { keypair ->
                require(keypair is Sr25519Keypair) {
                    "HOP chat key must be Sr25519, got ${keypair::class.simpleName}"
                }
                keypair
            }
            .flatMap { keypair ->
                slotAllocator.allocate(
                    target = keypair.publicKey.intoAccountId(),
                    strategy = OnExistingAllocationStrategy.IGNORE
                ).map { keypair }
            }
            .getOrThrow()
    }

    companion object {
        private const val DERIVATION_PATH = "//allowance//bulletin//chat"
    }
}
