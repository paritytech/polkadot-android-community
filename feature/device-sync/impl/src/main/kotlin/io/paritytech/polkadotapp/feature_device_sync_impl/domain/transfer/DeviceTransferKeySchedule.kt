package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer

import io.paritytech.polkadotapp.common.domain.model.AeadKey
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.blake2b256
import java.util.UUID

class DeviceTransferKeySchedule private constructor(
    val transferSeed: DataByteArray,
    val senderLabel: DataByteArray,
    val receiverLabel: DataByteArray,
    val requestTopic: DataByteArray,
    val requestKey: AeadKey,
    val activeDeviceTopic: DataByteArray,
    val activeDeviceKey: AeadKey,
) {
    fun sessionKey(requestId: UUID): AeadKey {
        return AeadKey.fromDerivedBytes(khash(transferSeed.value, SESSION_KEY_LABEL + requestId))
    }

    companion object {
        private const val TRANSFER_SEED_LABEL = "deviceTransfer"
        private const val SENDER_LABEL = "sender"
        private const val RECEIVER_LABEL = "receiver"
        private const val REQUEST_TOPIC_LABEL = "requestTopic"
        private const val REQUEST_KEY_LABEL = "requestKey"
        private const val SESSION_KEY_LABEL = "deviceTransferSession"
        private const val ACTIVE_DEVICE_TOPIC_LABEL = "activeDeviceTopic"
        private const val ACTIVE_DEVICE_KEY_LABEL = "activeDeviceEncryptionKey"

        fun fromEntropy(entropy: ByteArray): DeviceTransferKeySchedule {
            val transferSeed = khash(entropy, TRANSFER_SEED_LABEL)

            return DeviceTransferKeySchedule(
                transferSeed = transferSeed.toDataByteArray(),
                senderLabel = khash(transferSeed, SENDER_LABEL).toDataByteArray(),
                receiverLabel = khash(transferSeed, RECEIVER_LABEL).toDataByteArray(),
                requestTopic = khash(transferSeed, REQUEST_TOPIC_LABEL).toDataByteArray(),
                requestKey = AeadKey.fromDerivedBytes(khash(transferSeed, REQUEST_KEY_LABEL)),
                activeDeviceTopic = khash(entropy, ACTIVE_DEVICE_TOPIC_LABEL).toDataByteArray(),
                activeDeviceKey = AeadKey.fromDerivedBytes(khash(entropy, ACTIVE_DEVICE_KEY_LABEL)),
            )
        }

        private fun khash(key: ByteArray, data: String): ByteArray {
            return data.encodeToByteArray().blake2b256(key = key)
        }
    }
}
