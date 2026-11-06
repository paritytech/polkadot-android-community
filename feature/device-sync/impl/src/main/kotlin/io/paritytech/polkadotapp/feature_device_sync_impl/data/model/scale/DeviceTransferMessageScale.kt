package io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.annotations.EnumIndex
import kotlinx.serialization.Serializable

@Serializable
sealed interface DeviceTransferMessageScale {
    @Serializable
    @EnumIndex(0)
    class Request(val request: DeviceTransferRequestScale) : DeviceTransferMessageScale

    @Serializable
    @EnumIndex(1)
    class Cancel(val requestId: String) : DeviceTransferMessageScale
}

@Serializable
class DeviceTransferRequestScale(
    val requestId: String,
    val deviceModel: String,
    val operatingSystem: String,
    val createdAt: ULong,
)
