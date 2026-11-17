package io.paritytech.polkadotapp.feature_device_sync_impl.data.transfer

import io.paritytech.polkadotapp.common.data.encryption.MessageEncryption
import io.paritytech.polkadotapp.common.data.encryption.chaCha20Poly1305
import io.paritytech.polkadotapp.common.data.encryption.hkdfSha256
import io.paritytech.polkadotapp.common.domain.model.X25519PublicKey
import io.paritytech.polkadotapp.common.domain.model.X25519SharedSecret
import io.paritytech.polkadotapp.feature_statement_store_api.data.encryption.CommunicationEncryption

class PresharedKeyEncryption(
    override val sharedSecret: X25519SharedSecret,
    override val localPublicKey: X25519PublicKey,
) : CommunicationEncryption {
    private val messageEncryption = MessageEncryption.chaCha20Poly1305(hkdfSha256(sharedSecret.bytes.value))

    override fun encrypt(message: ByteArray): ByteArray = messageEncryption.encrypt(message)

    override fun decrypt(encryptedMessage: ByteArray): ByteArray = messageEncryption.decrypt(encryptedMessage)
}
