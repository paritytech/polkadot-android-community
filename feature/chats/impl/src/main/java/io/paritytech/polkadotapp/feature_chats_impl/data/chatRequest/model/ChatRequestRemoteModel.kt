package io.paritytech.polkadotapp.feature_chats_impl.data.chatRequest.model

import androidx.annotation.Keep
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.annotations.FixedLength
import io.paritytech.polkadotapp.feature_chats_transport_protocol.scale.VersionedRequestContent
import io.paritytech.polkadotapp.feature_statement_store_api.data.models.StatementProofRemote
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Encrypted model sent over the network.
 * Preserves requester anonymity at transport layer.
 */
@Serializable
class ChatRequestEncryptedRemote(
    val encryptionPubKey: ByteArray,
    val encryptedRequest: ByteArray
)

/**
 * Decrypted request obtained by acceptor.
 */
@Serializable
class ChatRequestDecrypted(
    val message: ChatRequestMessage,
    val proof: StatementProofRemote
)

/**
 * Proof payload for verification.
 * The requester signs this payload to prove ownership.
 */
@Serializable
@Keep
class ChatRequestProofPayload(
    val message: ChatRequestMessage,
    val acceptor: ByteArray
)

@Serializable
class ChatRequestMessage(
    val messageId: String,
    val timestamp: ULong,
    val content: VersionedRequestContent
) {
    companion object {
        fun new(content: VersionedRequestContent): ChatRequestMessage {
            return ChatRequestMessage(
                messageId = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis().toULong(),
                content = content
            )
        }
    }
}

@Serializable
@Keep
class IdentityProofPayloadScale(
    @FixedLength(32)
    val identityAccountId: ByteArray,
    @FixedLength(32)
    val statementAccountId: ByteArray,
    val context: String,
)
