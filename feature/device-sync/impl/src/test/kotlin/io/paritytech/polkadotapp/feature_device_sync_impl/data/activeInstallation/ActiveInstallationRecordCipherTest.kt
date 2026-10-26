package io.paritytech.polkadotapp.feature_device_sync_impl.data.activeInstallation

import io.paritytech.polkadotapp.common.data.encryption.MessageEncryption
import io.paritytech.polkadotapp.common.data.encryption.chaCha20Poly1305
import io.paritytech.polkadotapp.common.domain.model.AeadKey
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_device_sync_api.domain.activeInstallation.ActiveInstallation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Instant

class ActiveInstallationRecordCipherTest {
    private val cipher = ActiveInstallationRecordCipher()
    private val key = AeadKey.fromDerivedBytes(ByteArray(32) { 0x07 })
    private val installation = ActiveInstallation(
        installationId = ByteArray(32) { 0x01 }.toDataByteArray(),
        deviceModel = "Pixel 9",
        operatingSystem = "Android 16",
        activatedAt = Instant.fromEpochMilliseconds(1_700_000_000_000),
    )

    @Test
    fun `sealed record opens with the same key`() {
        assertEquals(installation, cipher.open(cipher.seal(installation, key), key))
    }

    @Test
    fun `record sealed under another key doesn't open`() {
        val otherKey = AeadKey.fromDerivedBytes(ByteArray(32) { 0x08 })

        assertNull(cipher.open(cipher.seal(installation, otherKey), key))
    }

    @Test
    fun `record of an unknown version doesn't open`() {
        val sealed = MessageEncryption.chaCha20Poly1305(key).encrypt(byteArrayOf(0x01) + ByteArray(48))

        assertNull(cipher.open(sealed, key))
    }

    @Test
    fun `bytes shorter than a nonce don't open`() {
        assertNull(cipher.open(ByteArray(4), key))
    }
}
