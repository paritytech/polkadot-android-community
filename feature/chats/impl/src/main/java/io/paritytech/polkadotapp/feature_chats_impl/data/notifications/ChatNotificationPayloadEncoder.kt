package io.paritytech.polkadotapp.feature_chats_impl.data.notifications

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.paritytech.polkadotapp.common.utils.InformationSize.Companion.bytes
import io.paritytech.polkadotapp.common.utils.encodeToByteArrayCatching
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage
import io.paritytech.polkadotapp.feature_chats_impl.data.model.toNotificationPayload
import io.paritytech.polkadotapp.feature_chats_impl.data.model.toStripped
import io.paritytech.polkadotapp.feature_chats_impl.data.model.toWireContent
import io.paritytech.polkadotapp.feature_chats_transport_protocol.scale.NotificationChatMessageContentV1
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.EncodedMessage
import timber.log.Timber
import javax.inject.Inject

class ChatNotificationPayloadEncoder @Inject constructor() {
    companion object {
        // APNs alert is the tightest target: 4096 B of JSON around hex(ciphertext) leaves ~1946 B of plaintext
        private val PAYLOAD_BUDGET = 1800.bytes
    }

    fun encode(message: ChatMessage): Result<EncodedMessage> {
        return message.toWireContent().flatMap { content ->
            encode(message, NotificationChatMessageContentV1.Full(content)).flatMap { full ->
                if (full.fitsBudget()) {
                    Result.success(full)
                } else {
                    content.toStripped()
                        .flatMap { encode(message, NotificationChatMessageContentV1.Stripped(it)) }
                        .onSuccess { stripped ->
                            if (!stripped.fitsBudget()) {
                                Timber.w("Stripped push payload of ${stripped.size} bytes still exceeds $PAYLOAD_BUDGET")
                            }
                        }
                }
            }
        }
    }

    private fun encode(message: ChatMessage, content: NotificationChatMessageContentV1): Result<EncodedMessage> {
        return BinaryScale.encodeToByteArrayCatching(message.toNotificationPayload(content))
    }

    private fun EncodedMessage.fitsBudget(): Boolean = size.bytes <= PAYLOAD_BUDGET
}
