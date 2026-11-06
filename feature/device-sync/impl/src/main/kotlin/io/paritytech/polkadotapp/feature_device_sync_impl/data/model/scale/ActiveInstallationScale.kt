package io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.annotations.EnumIndex
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.annotations.FixedLength
import kotlinx.serialization.Serializable

@Serializable
sealed interface VersionedActiveDeviceScale {
    @Serializable
    @EnumIndex(0)
    class V1(val installation: ActiveInstallationScale) : VersionedActiveDeviceScale
}

@Serializable
class ActiveInstallationScale(
    @FixedLength(32)
    val installationId: ByteArray,
    val deviceModel: String,
    val operatingSystem: String,
    val activatedAt: ULong,
)
