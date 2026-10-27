package io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale

import io.novasama.substrate_sdk_android.extensions.toHexString
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.encodeToByteArray
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTransferControlScaleConformanceTest {
    @Test
    fun `begin encodes to the frozen vector`() {
        assertEquals("00" + "05000000", encode(DeviceTransferControlScale.Begin(totalChanges = 5u)))
    }

    @Test
    fun `sender completed encodes to the frozen vector`() {
        val control = DeviceTransferControlScale.SenderCompleted(deviceKey = ByteArray(32) { 0x01 })

        assertEquals("01" + "01".repeat(32), encode(control))
    }

    @Test
    fun `receiver completed encodes to the frozen vector`() {
        assertEquals("02", encode(DeviceTransferControlScale.ReceiverCompleted))
    }

    @Test
    fun `abort encodes its reason`() {
        assertEquals("0300", encode(DeviceTransferControlScale.Abort(AbortReasonScale.USER_CANCELLED)))
        assertEquals("0301", encode(DeviceTransferControlScale.Abort(AbortReasonScale.INTERNAL_ERROR)))
    }

    private fun encode(control: DeviceTransferControlScale): String {
        return BinaryScale.encodeToByteArray<DeviceTransferControlScale>(control).toHexString()
    }
}
