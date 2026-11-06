package io.paritytech.polkadotapp.feature_device_sync_impl.data.transfer

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.encodeToByteArray
import io.paritytech.polkadotapp.common.data.encryption.MessageEncryption
import io.paritytech.polkadotapp.common.data.encryption.chaCha20Poly1305
import io.paritytech.polkadotapp.common.data.encryption.hkdfSha256
import io.paritytech.polkadotapp.common.domain.model.AeadKey
import io.paritytech.polkadotapp.common.utils.decodeFromByteArrayCatching
import io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer.DeviceTransferRequest
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.DeviceTransferMessageScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.DeviceTransferRequestScale
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.discovery.DeviceTransferMessage
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Instant

class DeviceTransferMessageCipher @Inject constructor() {
    fun seal(message: DeviceTransferMessage, requestKey: AeadKey): ByteArray {
        val encoded = BinaryScale.encodeToByteArray<DeviceTransferMessageScale>(message.toScale())

        return encryption(requestKey).encrypt(encoded)
    }

    fun open(sealed: ByteArray, requestKey: AeadKey): DeviceTransferMessage? {
        val encoded = runCatching { encryption(requestKey).decrypt(sealed) }.getOrNull() ?: return null
        val message = BinaryScale.decodeFromByteArrayCatching<DeviceTransferMessageScale>(encoded).getOrNull()
            ?: return null

        return runCatching { message.toDomain() }.getOrNull()
    }

    private fun encryption(requestKey: AeadKey): MessageEncryption {
        return MessageEncryption.chaCha20Poly1305(hkdfSha256(requestKey.bytes.value))
    }

    private fun DeviceTransferMessage.toScale(): DeviceTransferMessageScale = when (this) {
        is DeviceTransferMessage.Request -> DeviceTransferMessageScale.Request(request.toScale())
        is DeviceTransferMessage.Cancel -> DeviceTransferMessageScale.Cancel(requestId.toString())
    }

    private fun DeviceTransferRequest.toScale() = DeviceTransferRequestScale(
        requestId = requestId.toString(),
        deviceModel = deviceModel,
        operatingSystem = operatingSystem,
        createdAt = createdAt.toEpochMilliseconds().toULong(),
    )

    private fun DeviceTransferMessageScale.toDomain(): DeviceTransferMessage = when (this) {
        is DeviceTransferMessageScale.Request -> DeviceTransferMessage.Request(request.toDomain())
        is DeviceTransferMessageScale.Cancel -> DeviceTransferMessage.Cancel(UUID.fromString(requestId))
    }

    private fun DeviceTransferRequestScale.toDomain() = DeviceTransferRequest(
        requestId = UUID.fromString(requestId),
        deviceModel = deviceModel,
        operatingSystem = operatingSystem,
        createdAt = Instant.fromEpochMilliseconds(createdAt.toLong()),
    )
}
