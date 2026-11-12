package io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.annotations.EnumIndex
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.annotations.FixedLength
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.serializers.BigIntegerSerializable
import io.paritytech.polkadotapp.common.domain.model.scale.X25519PublicKeyScale
import io.paritytech.polkadotapp.feature_chats_transport_protocol.scale.LocalMessageScale
import io.paritytech.polkadotapp.feature_chats_transport_protocol.scale.RichTextContent
import io.paritytech.polkadotapp.feature_chats_transport_protocol.scale.TokenContent
import io.paritytech.polkadotapp.feature_chats_transport_protocol.scale.VersionedRequestContent
import kotlinx.serialization.Serializable

@Serializable
class SyncUpdateScale(
    val id: UInt,
    val entities: List<SyncEntityScale>,
    val timePoint: ULong,
)

@Serializable
class SyncUpdateAckScale(
    val id: UInt,
)

/** Outer envelope on the data channel — Update or Ack. */
@Serializable
sealed interface SyncMessageScale {
    @Serializable
    @EnumIndex(0)
    class Update(val update: SyncUpdateScale) : SyncMessageScale

    @Serializable
    @EnumIndex(1)
    class Ack(val ack: SyncUpdateAckScale) : SyncMessageScale
}

@Serializable
sealed interface SyncEntityScale {
    @Serializable
    @EnumIndex(0)
    class Devices(val devices: List<LocalDeviceScale>) : SyncEntityScale

    @Serializable
    @EnumIndex(1)
    class ChatsAdded(val chats: List<ChatIdScale>) : SyncEntityScale

    @Serializable
    @EnumIndex(2)
    class ChatsRemoved(val chats: List<ChatIdScale>) : SyncEntityScale

    @Serializable
    @EnumIndex(3)
    class Messages(val messages: List<LocalMessageScale>) : SyncEntityScale

    @Serializable
    @EnumIndex(4)
    class ChatsBlocked(val states: List<ChatBlockStateScale>) : SyncEntityScale

    @Serializable
    @EnumIndex(5)
    class IncomingRequestsAdded(val requests: List<IncomingChatRequestScale>) : SyncEntityScale

    @Serializable
    @EnumIndex(6)
    class IncomingRequestsStatusUpdated(val updates: List<IncomingRequestStatusUpdateScale>) : SyncEntityScale

    @Serializable
    @EnumIndex(7)
    class OutgoingRequestsAdded(val requests: List<OutgoingChatRequestScale>) : SyncEntityScale

    @Serializable
    @EnumIndex(8)
    class OutgoingRequestsStatusUpdated(val updates: List<OutgoingRequestStatusUpdateScale>) : SyncEntityScale

    @Serializable
    @EnumIndex(9)
    class CoinsHandedOff(val coins: List<HandedOffCoinScale>) : SyncEntityScale

    @Serializable
    @EnumIndex(10)
    class CoinageMessagesStatusUpdated(val updates: List<CoinageMessageStatusUpdateScale>) : SyncEntityScale

    @Serializable
    @EnumIndex(11)
    class ContactDevicesChanged(val changes: List<ContactDeviceChangeScale>) : SyncEntityScale
}

@Serializable
class HandedOffCoinScale(
    @FixedLength(32)
    val installationId: ByteArray,
    val derivationIndex: ULong,
    val exponent: Short,
)

@Serializable
class CoinageMessageStatusUpdateScale(
    val chatId: ChatIdScale,
    val messageId: String,
    val status: CoinageMessageStatusScale,
)

@Serializable
sealed interface CoinageMessageStatusScale {
    @Serializable
    @EnumIndex(0)
    data object None : CoinageMessageStatusScale

    @Serializable
    @EnumIndex(1)
    class Claimed(val partial: BigIntegerSerializable?) : CoinageMessageStatusScale

    @Serializable
    @EnumIndex(2)
    data object Failed : CoinageMessageStatusScale
}

@Serializable
class LocalDeviceScale(
    @FixedLength(32)
    val statementAccountId: ByteArray,
    val encryptionPublicKey: X25519PublicKeyScale,
    val status: DeviceStatusScale,
    val lastUpdate: ULong,
)

@Serializable
enum class DeviceStatusScale {
    @EnumIndex(0)
    ACTIVE,
}

@Serializable
sealed interface ChatIdScale {
    @Serializable
    @EnumIndex(0)
    class Contact(
        @FixedLength(32)
        val accountId: ByteArray,
    ) : ChatIdScale
}

@Serializable
class ChatBlockStateScale(
    val chatId: ChatIdScale,
    val blocked: Boolean,
)

@Serializable
enum class IncomingRequestStatusScale {
    @EnumIndex(0)
    PENDING,

    @EnumIndex(1)
    ACCEPTED,

    @EnumIndex(2)
    DECLINED,
}

@Serializable
class IncomingChatRequestScale(
    val requestId: String,
    @FixedLength(32)
    val peer: ByteArray,
    val timestamp: ULong,
    val status: IncomingRequestStatusScale,
    val peerDevice: LocalDeviceScale?,
    val pushToken: TokenContent?,
    val welcomeMessage: RichTextContent?,
)

@Serializable
class IncomingRequestStatusUpdateScale(
    val requestId: String,
    val status: IncomingRequestStatusScale,
)

@Serializable
enum class OutgoingRequestStatusScale {
    @EnumIndex(0)
    PENDING,

    @EnumIndex(1)
    ACCEPTED,

    @EnumIndex(2)
    CANCELLED,
}

@Serializable
class OutgoingChatRequestScale(
    val requestId: String,
    @FixedLength(32)
    val peer: ByteArray,
    val timestamp: ULong,
    val status: OutgoingRequestStatusScale,
    val content: VersionedRequestContent,
)

@Serializable
class OutgoingRequestStatusUpdateScale(
    val requestId: String,
    val status: OutgoingRequestStatusScale,
)

@Serializable
class ContactDeviceChangeScale(
    val chatId: ChatIdScale,
    @FixedLength(32)
    val statementAccountId: ByteArray,
    val encryptionPublicKey: X25519PublicKeyScale,
    val status: ContactDeviceStatusScale,
    val changedAt: ULong,
)

@Serializable
enum class ContactDeviceStatusScale {
    @EnumIndex(0)
    ACTIVE,

    @EnumIndex(1)
    INACTIVE,
}
