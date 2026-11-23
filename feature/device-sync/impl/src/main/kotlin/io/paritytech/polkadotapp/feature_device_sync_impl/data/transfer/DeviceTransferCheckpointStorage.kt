package io.paritytech.polkadotapp.feature_device_sync_impl.data.transfer

import io.novasama.substrate_sdk_android.extensions.toHexString
import io.paritytech.polkadotapp.common.data.storage.preferences.Preferences
import io.paritytech.polkadotapp.common.data.storage.preferences.edit
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import javax.inject.Inject

class DeviceTransferCheckpointStorage @Inject constructor(
    private val preferences: Preferences,
) {
    fun getCheckpoint(senderInstallationId: DataByteArray): ULong? {
        val savedSenderInstallationId = preferences.getString(KEY_SENDER_INSTALLATION_ID)

        return preferences.getLong(KEY_TIME_POINT, 0L).toULong()
            .takeIf { savedSenderInstallationId == senderInstallationId.value.toHexString() }
    }

    fun saveCheckpoint(senderInstallationId: DataByteArray, timePoint: ULong) {
        preferences.edit {
            putString(KEY_SENDER_INSTALLATION_ID, senderInstallationId.value.toHexString())
            putLong(KEY_TIME_POINT, timePoint.toLong())
        }
    }

    private companion object {
        const val KEY_SENDER_INSTALLATION_ID = "device_transfer_checkpoint_sender_installation_id"
        const val KEY_TIME_POINT = "device_transfer_checkpoint_time_point"
    }
}
