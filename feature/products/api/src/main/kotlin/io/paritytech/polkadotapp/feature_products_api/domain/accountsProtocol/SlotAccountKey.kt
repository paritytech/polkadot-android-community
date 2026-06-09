package io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol

import io.novasama.substrate_sdk_android.encrypt.keypair.substrate.Sr25519SubstrateKeypairFactory
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray

/**
 * 32-byte sr25519 private key concatenated with 32-byte nonce (64 bytes total).
 * Public key is derived deterministically when needed.
 */
@JvmInline
value class SlotAccountKey(val bytes: DataByteArray)

fun SlotAccountKey.deriveAccountId(): AccountId {
    val keypair = Sr25519SubstrateKeypairFactory.createKeypairFromSecret(bytes.value)
    return keypair.publicKey.toDataByteArray()
}
