package io.paritytech.polkadotapp.feature_device_sync_impl.data.transfer

import io.novasama.substrate_sdk_android.extensions.toHexString
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.encodeToByteArray
import io.paritytech.polkadotapp.common.data.encryption.MessageEncryption
import io.paritytech.polkadotapp.common.data.encryption.chaCha20Poly1305
import io.paritytech.polkadotapp.common.data.encryption.hkdfSha256
import io.paritytech.polkadotapp.common.domain.model.AeadKey
import io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer.DeviceTransferRequest
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.DeviceTransferMessageScale
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.DeviceTransferKeySchedule
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.discovery.DeviceTransferMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID
import kotlin.time.Instant

class DeviceTransferMessageCipherTest {
    private val cipher = DeviceTransferMessageCipher()
    private val requestKey = DeviceTransferKeySchedule.fromEntropy(ByteArray(16) { 0x01 }).requestKey
    private val requestId = UUID.fromString(REQUEST_ID)

    @Test
    fun `sealed request opens with the same key`() {
        val message = DeviceTransferMessage.Request(
            DeviceTransferRequest(
                requestId = requestId,
                deviceModel = "Pixel 9",
                operatingSystem = "Android 16",
                createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000),
            )
        )

        assertEquals(message, cipher.open(cipher.seal(message, requestKey), requestKey))
    }

    @Test
    fun `sealed cancel opens with the same key`() {
        val message = DeviceTransferMessage.Cancel(requestId)

        assertEquals(message, cipher.open(cipher.seal(message, requestKey), requestKey))
    }

    @Test
    fun `message sealed under another key doesn't open`() {
        val otherKey = DeviceTransferKeySchedule.fromEntropy(ByteArray(16) { 0x02 }).requestKey

        assertNull(cipher.open(cipher.seal(DeviceTransferMessage.Cancel(requestId), otherKey), requestKey))
    }

    @Test
    fun `message is sealed with the HKDF of the request key`() {
        val sealed = cipher.seal(DeviceTransferMessage.Cancel(requestId), requestKey)

        val decrypted = hkdfEncryption(requestKey).decrypt(sealed)

        assertEquals("01" + "90" + REQUEST_ID.encodeToByteArray().toHexString(), decrypted.toHexString())
    }

    @Test
    fun `upper case request id from another platform opens`() {
        val sealed = sealScale(DeviceTransferMessageScale.Cancel(REQUEST_ID.uppercase()))

        assertEquals(DeviceTransferMessage.Cancel(requestId), cipher.open(sealed, requestKey))
    }

    @Test
    fun `request id that isn't a UUID doesn't open`() {
        val sealed = sealScale(DeviceTransferMessageScale.Cancel("not-a-uuid"))

        assertNull(cipher.open(sealed, requestKey))
    }

    private fun sealScale(message: DeviceTransferMessageScale): ByteArray {
        return hkdfEncryption(requestKey).encrypt(BinaryScale.encodeToByteArray<DeviceTransferMessageScale>(message))
    }

    private fun hkdfEncryption(key: AeadKey): MessageEncryption {
        return MessageEncryption.chaCha20Poly1305(hkdfSha256(key.bytes.value))
    }

    private companion object {
        const val REQUEST_ID = "6f1c2a9e-3b4d-4e5f-8a7b-0c1d2e3f4a5b"
    }
}
