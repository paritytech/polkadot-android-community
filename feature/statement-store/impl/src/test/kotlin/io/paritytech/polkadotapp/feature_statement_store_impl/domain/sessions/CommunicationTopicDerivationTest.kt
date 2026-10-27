package io.paritytech.polkadotapp.feature_statement_store_impl.domain.sessions

import io.novasama.substrate_sdk_android.extensions.toHexString
import io.paritytech.polkadotapp.common.domain.model.X25519PublicKey
import io.paritytech.polkadotapp.common.domain.model.X25519SharedSecret
import io.paritytech.polkadotapp.common.domain.model.hexToDataByteArray
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_statement_store_api.data.encryption.CommunicationEncryption
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.SessionAccount
import io.paritytech.polkadotapp.feature_statement_store_impl.domain.sessions.codec.StatementChannelCreator
import org.junit.Assert.assertEquals
import org.junit.Test

class CommunicationTopicDerivationTest {
    private val encryption = FixedSecretEncryption(SESSION_KEY)
    private val transferSender = SessionAccount.Local(accountId = SENDER_LABEL.hexToDataByteArray(), pin = null)
    private val transferReceiver = SessionAccount.Local(accountId = RECEIVER_LABEL.hexToDataByteArray(), pin = null)
    private val channelCreator = StatementChannelCreator()

    @Test
    fun `session topics and request channels match the device transfer RFC vectors`() {
        val receiverToSender = deriveCommunicationTopic(sender = transferReceiver, receiver = transferSender, encryption)
        val senderToReceiver = deriveCommunicationTopic(sender = transferSender, receiver = transferReceiver, encryption)

        assertEquals(
            "c0c5c7eb24816b3c79506ad83012360860cf0ecce4274a8b9e58f1b80824c84f",
            receiverToSender.toHexString()
        )
        assertEquals(
            "b080338b43474752b88e95fc32f7f5b6b866d9d1276b8ee1573459877e9c20a5",
            senderToReceiver.toHexString()
        )
        assertEquals(
            "d0764d141a5e047eb9b063d59f3abe9188b6955784b6938e86cc733563564450",
            channelCreator.requestChannel(receiverToSender).toHexString()
        )
        assertEquals(
            "82c82417fccd9e0d23327a524046d5fd7388f228acd82277b7a9b9b4ff133ee4",
            channelCreator.requestChannel(senderToReceiver).toHexString()
        )
    }

    private class FixedSecretEncryption(secretHex: String) : CommunicationEncryption {
        override val sharedSecret: X25519SharedSecret =
            X25519SharedSecret.fromBytes(secretHex.hexToDataByteArray()).getOrThrow()

        override val localPublicKey: X25519PublicKey =
            X25519PublicKey.fromBytes(ByteArray(X25519PublicKey.SIZE_BYTES).toDataByteArray()).getOrThrow()

        override fun encrypt(message: ByteArray): ByteArray = message

        override fun decrypt(encryptedMessage: ByteArray): ByteArray = encryptedMessage
    }

    private companion object {
        const val SESSION_KEY = "4a631c9f4173513c97cfd490250313bca445e4ae24347712b50d9954913c80bf"
        const val SENDER_LABEL = "c698eeb293f2520a130d9ec22c68ae5359ec1b92df0523fba4bbae0471129746"
        const val RECEIVER_LABEL = "78e71f96a97df2279bb3ee3938fb329c71010bea54f34d265c5f5e97bf629b4f"
    }
}
