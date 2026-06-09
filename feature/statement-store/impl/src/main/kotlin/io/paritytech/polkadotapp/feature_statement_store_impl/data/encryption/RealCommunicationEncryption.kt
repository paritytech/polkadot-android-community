package io.paritytech.polkadotapp.feature_statement_store_impl.data.encryption

import io.paritytech.polkadotapp.common.data.encryption.MessageEncryption
import io.paritytech.polkadotapp.common.data.encryption.aes
import io.paritytech.polkadotapp.common.domain.model.EncodedPublicKey
import io.paritytech.polkadotapp.common.utils.ecdhSharedSecret
import io.paritytech.polkadotapp.feature_statement_store_api.data.encryption.CommunicationEncryption
import java.security.KeyPair
import java.security.PublicKey

internal class RealCommunicationEncryption(
    override val localPublicKey: EncodedPublicKey,
    private val localKeypair: KeyPair,
    remotePublicKey: PublicKey,
) : CommunicationEncryption {
    override val sharedSecret: ByteArray = generateSharedSecret(remotePublicKey)

    private val messageEncryption: MessageEncryption = MessageEncryption.aes(hkdfSha256(sharedSecret))

    override fun encrypt(message: ByteArray): ByteArray = messageEncryption.encrypt(message)

    override fun decrypt(encryptedMessage: ByteArray): ByteArray = messageEncryption.decrypt(encryptedMessage)

    private fun generateSharedSecret(remotePublicKey: PublicKey): ByteArray {
        return ecdhSharedSecret(localKeypair.private, remotePublicKey)
    }
}
