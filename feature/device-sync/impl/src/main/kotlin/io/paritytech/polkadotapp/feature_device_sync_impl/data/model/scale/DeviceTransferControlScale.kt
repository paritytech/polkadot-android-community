package io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.annotations.EnumIndex
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.annotations.FixedLength
import kotlinx.serialization.Serializable

@Serializable
sealed interface DeviceTransferControlScale {
    @Serializable
    @EnumIndex(0)
    class SenderStarted(
        @FixedLength(32)
        val deviceEncryptionKey: ByteArray,
        val totalChanges: UInt,
    ) : DeviceTransferControlScale

    @Serializable
    @EnumIndex(1)
    data object SenderCompleted : DeviceTransferControlScale

    @Serializable
    @EnumIndex(2)
    data object ReceiverCompleted : DeviceTransferControlScale

    @Serializable
    @EnumIndex(3)
    class Abort(val reason: AbortReasonScale) : DeviceTransferControlScale

    @Serializable
    @EnumIndex(4)
    class Resume(val fromTimePoint: ULong?) : DeviceTransferControlScale
}

@Serializable
enum class AbortReasonScale {
    @EnumIndex(0)
    USER_CANCELLED,

    @EnumIndex(1)
    INTERNAL_ERROR,
}
