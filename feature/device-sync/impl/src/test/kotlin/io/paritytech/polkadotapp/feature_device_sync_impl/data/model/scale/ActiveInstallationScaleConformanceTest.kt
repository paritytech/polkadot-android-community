package io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale

import io.novasama.substrate_sdk_android.extensions.fromHex
import io.novasama.substrate_sdk_android.extensions.toHexString
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.decodeFromByteArray
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.encodeToByteArray
import io.paritytech.polkadotapp.common.utils.decodeFromByteArrayCatching
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveInstallationScaleConformanceTest {
    private val recordHex = "00" + "01".repeat(32) + "1c506978656c2039" + "28416e64726f6964203136" + "0068e5cf8b010000"

    @Test
    fun `v1 record encodes to the frozen vector`() {
        val record = VersionedActiveDeviceScale.V1(
            ActiveInstallationScale(
                installationId = ByteArray(32) { 0x01 },
                deviceModel = "Pixel 9",
                operatingSystem = "Android 16",
                activatedAt = 1_700_000_000_000u,
            )
        )

        assertEquals(recordHex, BinaryScale.encodeToByteArray<VersionedActiveDeviceScale>(record).toHexString())
    }

    @Test
    fun `frozen vector decodes to the v1 record`() {
        val record = BinaryScale.decodeFromByteArray<VersionedActiveDeviceScale>(recordHex.fromHex())
        val installation = (record as VersionedActiveDeviceScale.V1).installation

        assertArrayEquals(ByteArray(32) { 0x01 }, installation.installationId)
        assertEquals("Pixel 9", installation.deviceModel)
        assertEquals("Android 16", installation.operatingSystem)
        assertEquals(1_700_000_000_000uL, installation.activatedAt)
    }

    @Test
    fun `unknown version doesn't decode`() {
        val unknownVersionHex = "01" + recordHex.drop(2)

        assertTrue(
            BinaryScale.decodeFromByteArrayCatching<VersionedActiveDeviceScale>(unknownVersionHex.fromHex()).isFailure
        )
    }
}
