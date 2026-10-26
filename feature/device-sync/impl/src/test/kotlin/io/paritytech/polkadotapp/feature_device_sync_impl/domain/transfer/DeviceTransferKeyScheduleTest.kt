package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer

import io.novasama.substrate_sdk_android.extensions.fromHex
import io.novasama.substrate_sdk_android.extensions.toHexString
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class DeviceTransferKeyScheduleTest {
    private val schedule = DeviceTransferKeySchedule.fromEntropy("000102030405060708090a0b0c0d0e0f".fromHex())

    @Test
    fun `entropy-derived keys match the RFC test vectors`() {
        assertEquals(
            "1268901b5cfb153e286c4f07e30566b4ed7d0de53e2aa18cb5bd1e8741df1865",
            schedule.transferSeed.value.toHexString()
        )
        assertEquals(
            "c698eeb293f2520a130d9ec22c68ae5359ec1b92df0523fba4bbae0471129746",
            schedule.senderLabel.value.toHexString()
        )
        assertEquals(
            "78e71f96a97df2279bb3ee3938fb329c71010bea54f34d265c5f5e97bf629b4f",
            schedule.receiverLabel.value.toHexString()
        )
        assertEquals(
            "259721d79610866b25e13e8fbd8510fee77afa17ee7c550ca37b8c8beb6c25db",
            schedule.requestTopic.value.toHexString()
        )
        assertEquals(
            "69261f4511e8ff2663af1f34d7ca99fb59df16f52fd1c1615e0f920c88173e7e",
            schedule.requestKey.bytes.value.toHexString()
        )
        assertEquals(
            "083ddfa0ed96eeb898535425fd7525843fead54f834d03de5daba6cda72aad7c",
            schedule.activeDeviceTopic.value.toHexString()
        )
        assertEquals(
            "a3b0345e394e7a92291c7613ad694c1751d93def13041301180d41e43af5731b",
            schedule.activeDeviceKey.bytes.value.toHexString()
        )
    }

    @Test
    fun `session key matches the RFC test vector whatever the request id's letter case`() {
        listOf(REQUEST_ID, REQUEST_ID.uppercase()).forEach { requestId ->
            val sessionKey = schedule.sessionKey(UUID.fromString(requestId))

            assertEquals(
                "4a631c9f4173513c97cfd490250313bca445e4ae24347712b50d9954913c80bf",
                sessionKey.bytes.value.toHexString()
            )
        }
    }

    private companion object {
        const val REQUEST_ID = "6f1c2a9e-3b4d-4e5f-8a7b-0c1d2e3f4a5b"
    }
}
