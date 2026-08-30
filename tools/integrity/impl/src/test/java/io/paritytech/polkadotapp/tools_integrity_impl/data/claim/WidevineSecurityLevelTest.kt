package io.paritytech.polkadotapp.tools_integrity_impl.data.claim

import android.media.NotProvisionedException
import android.media.ResourceBusyException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WidevineSecurityLevelTest {
    private val session = byteArrayOf(0x01)

    @Test
    fun `hardware secure session measures L1 and closes the session`() {
        val closed = mutableListOf<ByteArray>()

        val measuredL1 = WidevineEvidenceReader.measureHardwareSecure(
            openSession = { session },
            closeSession = closed::add,
            provision = { error("must not provision") }
        )

        assertTrue(measuredL1)
        assertEquals(listOf(session), closed)
    }

    @Test
    fun `unsupported hardware level is an honest non-L1 measurement`() {
        val measuredL1 = WidevineEvidenceReader.measureHardwareSecure(
            openSession = { throw IllegalArgumentException("level unsupported") },
            closeSession = { error("no session to close") },
            provision = { error("must not provision") }
        )

        assertFalse(measuredL1)
    }

    @Test
    fun `unprovisioned device provisions once and retries`() {
        var provisioned = false

        val measuredL1 = WidevineEvidenceReader.measureHardwareSecure(
            openSession = { if (provisioned) session else throw NotProvisionedException("fresh device") },
            closeSession = {},
            provision = { provisioned = true }
        )

        assertTrue(measuredL1)
        assertTrue(provisioned)
    }

    @Test
    fun `provisioning failure aborts`() {
        assertThrows(WidevineUnavailableException::class.java) {
            WidevineEvidenceReader.measureHardwareSecure(
                openSession = { throw NotProvisionedException("fresh device") },
                closeSession = {},
                provision = { throw IllegalStateException("server down") }
            )
        }
    }

    @Test
    fun `still unprovisioned after provisioning aborts`() {
        var provisions = 0

        assertThrows(WidevineUnavailableException::class.java) {
            WidevineEvidenceReader.measureHardwareSecure(
                openSession = { throw NotProvisionedException("stays unprovisioned") },
                closeSession = {},
                provision = { provisions++ }
            )
        }

        assertEquals(1, provisions)
    }

    @Test
    fun `busy drm aborts`() {
        assertThrows(WidevineUnavailableException::class.java) {
            WidevineEvidenceReader.measureHardwareSecure(
                openSession = { throw ResourceBusyException("busy") },
                closeSession = {},
                provision = {}
            )
        }
    }
}
