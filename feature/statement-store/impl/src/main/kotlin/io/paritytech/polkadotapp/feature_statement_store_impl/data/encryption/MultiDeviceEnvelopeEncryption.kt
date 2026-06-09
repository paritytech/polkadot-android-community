package io.paritytech.polkadotapp.feature_statement_store_impl.data.encryption

import io.paritytech.polkadotapp.common.data.encryption.MessageEncryption
import io.paritytech.polkadotapp.common.data.encryption.aes
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.domain.model.EncodedPublicKey
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.Secp256r1KeyGenerator
import io.paritytech.polkadotapp.common.utils.ecdhSharedSecret
import io.paritytech.polkadotapp.feature_statement_store_api.domain.OurDeviceKeypairProvider
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.DeviceInfo
import io.paritytech.polkadotapp.feature_statement_store_impl.data.models.scale.RequestDeviceInfo
import java.security.PublicKey
import java.security.SecureRandom
import javax.inject.Inject

/**
 * Wraps/unwraps chat payloads addressed to multiple recipient devices of one peer.
 *
 * A fresh 256-bit AES key (PK) encrypts the payload; PK is then re-encrypted for each
 * recipient device with a per-device AES key derived from ECDH (via [deriveSharedSecretWith])
 * × that device's encryption public key. Receivers look up their own entry in `devicesInfo`
 * by statement account id and reverse the flow.
 *
 * Bound to "our device" ([ourStatementAccountId] + [ourEncryptionPublicKey]); the private
 * half is hidden behind [deriveSharedSecretWith]. Build via [Factory] per session.
 */
class MultiDeviceEnvelopeEncryption(
    private val ourStatementAccountId: AccountId,
    private val keyGenerator: Secp256r1KeyGenerator,
    private val ourDeviceKeypairProvider: OurDeviceKeypairProvider
) {
    class Factory @Inject constructor(
        private val ourDeviceKeypairProvider: OurDeviceKeypairProvider,
        private val keyGenerator: Secp256r1KeyGenerator,
    ) {
        fun create(ourStatementAccountId: AccountId): MultiDeviceEnvelopeEncryption = MultiDeviceEnvelopeEncryption(
            ourStatementAccountId = ourStatementAccountId,
            keyGenerator = keyGenerator,
            ourDeviceKeypairProvider = ourDeviceKeypairProvider
        )
    }

    data class Recipient(
        val statementAccountId: AccountId,
        val encryptionPublicKey: EncodedPublicKey,
    )

    data class WrappedPayload(
        val encryptedPayload: DataByteArray,
        val devicesInfo: List<RequestDeviceInfo>,
    )

    /** @throws IllegalArgumentException if [recipients] is empty. */
    suspend fun wrap(
        payload: ByteArray,
        recipients: List<Recipient>,
    ): WrappedPayload {
        require(recipients.isNotEmpty()) {
            "Cannot wrap multi-device payload with no recipient devices"
        }

        val symmetricKey = generateRandomAesKey()
        val payloadEncryption = MessageEncryption.aes(symmetricKey)
        val encryptedPayload = payloadEncryption.encrypt(payload)

        val devicesInfo = recipients.map { recipient ->
            val perDeviceKey = deriveEnvelopeAesKey(recipient.encryptionPublicKey)
            val encryptedKey = MessageEncryption.aes(perDeviceKey).encrypt(symmetricKey)

            RequestDeviceInfo(
                statementAccountId = recipient.statementAccountId.value,
                encryptedKey = encryptedKey.toDataByteArray(),
            )
        }

        return WrappedPayload(encryptedPayload.toDataByteArray(), devicesInfo)
    }

    suspend fun unwrap(
        encryptedPayload: ByteArray,
        devicesInfo: List<RequestDeviceInfo>,
        senderEncryptionPublicKey: EncodedPublicKey,
    ): ByteArray {
        val ownEntry = devicesInfo.firstOrNull { it.statementAccountId.toDataByteArray() == ourStatementAccountId }
            ?: error("Multi-device envelope is not addressed to this device")

        val perDeviceKey = deriveEnvelopeAesKey(senderEncryptionPublicKey)
        val symmetricKey = MessageEncryption.aes(perDeviceKey).decrypt(ownEntry.encryptedKey.value)

        return MessageEncryption.aes(symmetricKey).decrypt(encryptedPayload)
    }

    suspend fun unwrapOwn(
        encryptedPayload: ByteArray,
        devicesInfo: List<RequestDeviceInfo>,
        peerDevices: List<Recipient>,
    ): ByteArray {
        val peersByAccount = peerDevices.associateBy { it.statementAccountId }
        val entry = devicesInfo.firstOrNull { peersByAccount.containsKey(it.statementAccountId.toDataByteArray()) }
            ?: error("Own multi-device envelope has no known peer device entry")

        val peerPublicKey = peersByAccount.getValue(entry.statementAccountId.toDataByteArray()).encryptionPublicKey
        val perDeviceKey = deriveEnvelopeAesKey(peerPublicKey)
        val symmetricKey = MessageEncryption.aes(perDeviceKey).decrypt(entry.encryptedKey.value)

        return MessageEncryption.aes(symmetricKey).decrypt(encryptedPayload)
    }

    private suspend fun deriveEnvelopeAesKey(peerPublicKey: EncodedPublicKey): ByteArray {
        val publicKey = keyGenerator.derivePublicKey(peerPublicKey.value)
        val sharedSecret = deriveSharedSecretWith(publicKey)
        return hkdfSha256(sharedSecret)
    }

    private fun generateRandomAesKey(): ByteArray {
        return ByteArray(AES_KEY_SIZE_BYTES).apply { SecureRandom().nextBytes(this) }
    }

    private suspend fun deriveSharedSecretWith(publicKey: PublicKey): ByteArray {
        val keypair = ourDeviceKeypairProvider.get()
        return ecdhSharedSecret(keypair.private, publicKey)
    }
}

fun List<DeviceInfo>.toEnvelopeRecipients(): List<MultiDeviceEnvelopeEncryption.Recipient> = map {
    MultiDeviceEnvelopeEncryption.Recipient(
        statementAccountId = it.statementAccountId,
        encryptionPublicKey = it.encryptionPublicKey,
    )
}
