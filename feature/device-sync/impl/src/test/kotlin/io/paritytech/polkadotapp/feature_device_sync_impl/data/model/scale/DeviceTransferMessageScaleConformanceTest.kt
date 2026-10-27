package io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale

import io.novasama.substrate_sdk_android.extensions.fromHex
import io.novasama.substrate_sdk_android.extensions.toHexString
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.decodeFromByteArray
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.encodeToByteArray
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTransferMessageScaleConformanceTest {
    private val requestIdHex = "9036663163326139652d336234642d346535662d386137622d306331643265336634613562"
    private val requestHex = "00" + requestIdHex + "1c506978656c2039" + "28416e64726f6964203136" + "0068e5cf8b010000"
    private val cancelHex = "01" + requestIdHex

    @Test
    fun `request encodes to the frozen vector`() {
        val message = DeviceTransferMessageScale.Request(
            DeviceTransferRequestScale(
                requestId = REQUEST_ID,
                deviceModel = "Pixel 9",
                operatingSystem = "Android 16",
                createdAt = 1_700_000_000_000u,
            )
        )

        assertEquals(requestHex, BinaryScale.encodeToByteArray<DeviceTransferMessageScale>(message).toHexString())
    }

    @Test
    fun `cancel encodes to the frozen vector`() {
        val message = DeviceTransferMessageScale.Cancel(requestId = REQUEST_ID)

        assertEquals(cancelHex, BinaryScale.encodeToByteArray<DeviceTransferMessageScale>(message).toHexString())
    }

    @Test
    fun `frozen vectors decode to the request and the cancel`() {
        val request = BinaryScale.decodeFromByteArray<DeviceTransferMessageScale>(requestHex.fromHex())
        val cancel = BinaryScale.decodeFromByteArray<DeviceTransferMessageScale>(cancelHex.fromHex())

        val decodedRequest = (request as DeviceTransferMessageScale.Request).request
        assertEquals(REQUEST_ID, decodedRequest.requestId)
        assertEquals("Pixel 9", decodedRequest.deviceModel)
        assertEquals("Android 16", decodedRequest.operatingSystem)
        assertEquals(1_700_000_000_000uL, decodedRequest.createdAt)
        assertEquals(REQUEST_ID, (cancel as DeviceTransferMessageScale.Cancel).requestId)
    }

    private companion object {
        const val REQUEST_ID = "6f1c2a9e-3b4d-4e5f-8a7b-0c1d2e3f4a5b"
    }
}
