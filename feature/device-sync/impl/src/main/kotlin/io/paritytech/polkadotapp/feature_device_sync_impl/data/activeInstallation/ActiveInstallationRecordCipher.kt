package io.paritytech.polkadotapp.feature_device_sync_impl.data.activeInstallation

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.encodeToByteArray
import io.paritytech.polkadotapp.common.data.encryption.MessageEncryption
import io.paritytech.polkadotapp.common.data.encryption.chaCha20Poly1305
import io.paritytech.polkadotapp.common.domain.model.AeadKey
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.decodeFromByteArrayCatching
import io.paritytech.polkadotapp.feature_device_sync_api.domain.activeInstallation.ActiveInstallation
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.ActiveInstallationScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.VersionedActiveDeviceScale
import javax.inject.Inject
import kotlin.time.Instant

class ActiveInstallationRecordCipher @Inject constructor() {
    fun seal(installation: ActiveInstallation, key: AeadKey): ByteArray {
        val record = VersionedActiveDeviceScale.V1(installation.toScale())
        val encoded = BinaryScale.encodeToByteArray<VersionedActiveDeviceScale>(record)

        return MessageEncryption.chaCha20Poly1305(key).encrypt(encoded)
    }

    fun open(sealed: ByteArray, key: AeadKey): ActiveInstallation? {
        val encoded = runCatching { MessageEncryption.chaCha20Poly1305(key).decrypt(sealed) }.getOrNull() ?: return null
        val record = BinaryScale.decodeFromByteArrayCatching<VersionedActiveDeviceScale>(encoded).getOrNull()

        return when (record) {
            is VersionedActiveDeviceScale.V1 -> record.installation.toDomain()
            null -> null
        }
    }

    private fun ActiveInstallation.toScale() = ActiveInstallationScale(
        installationId = installationId.value,
        deviceModel = deviceModel,
        operatingSystem = operatingSystem,
        activatedAt = activatedAt.toEpochMilliseconds().toULong(),
    )

    private fun ActiveInstallationScale.toDomain() = ActiveInstallation(
        installationId = installationId.toDataByteArray(),
        deviceModel = deviceModel,
        operatingSystem = operatingSystem,
        activatedAt = Instant.fromEpochMilliseconds(activatedAt.toLong()),
    )
}
