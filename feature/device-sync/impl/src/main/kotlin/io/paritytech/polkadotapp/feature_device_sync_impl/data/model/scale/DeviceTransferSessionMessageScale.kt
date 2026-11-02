package io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.annotations.EnumIndex
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.signaling.SyncSignalingEnvelope
import kotlinx.serialization.Serializable

@Serializable
sealed interface DeviceTransferSessionMessageScale {
    @Serializable
    @EnumIndex(0)
    data object Rejected : DeviceTransferSessionMessageScale

    @Serializable
    @EnumIndex(1)
    class Signaling(val envelope: SyncSignalingEnvelope) : DeviceTransferSessionMessageScale
}
