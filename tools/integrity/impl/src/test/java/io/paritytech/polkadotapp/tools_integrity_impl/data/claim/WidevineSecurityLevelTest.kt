package io.paritytech.polkadotapp.tools_integrity_impl.data.claim

import android.media.NotProvisionedException
import android.media.ResourceBusyException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WidevineSecurityLevelTest {
    private val session = byteArrayOf(0x01)

    @Test
    fun `hardware secure session measures L1 and closes the session`() {
        val closed = mutableListOf<ByteArray>()

        val level = WidevineEvidenceReader.measureSecurityLevel(
            openSession = { session },
            closeSession = closed::add,
            provision = { error("must not provision") }
        )

        assertEquals(WIDEVINE_LEVEL_L1, level)
        assertEquals(listOf(session), closed)
    }

    @Test
    fun `unsupported hardware level measures L3`() {
        val level = WidevineEvidenceReader.measureSecurityLevel(
            openSession = { throw IllegalArgumentException("level unsupported") },
            closeSession = { error("no session to close") },
            provision = { error("must not provision") }
        )

        assertEquals(WIDEVINE_LEVEL_L3, level)
    }

    @Test
    fun `unprovisioned device provisions once and retries`() {
        var provisioned = false

        val level = WidevineEvidenceReader.measureSecurityLevel(
            openSession = { if (provisioned) session else throw NotProvisionedException("fresh device") },
            closeSession = {},
            provision = { provisioned = true }
        )

        assertEquals(WIDEVINE_LEVEL_L1, level)
        assertTrue(provisioned)
    }

    @Test
    fun `provisioning failure aborts`() {
        assertThrows(WidevineUnavailableException::class.java) {
            WidevineEvidenceReader.measureSecurityLevel(
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
            WidevineEvidenceReader.measureSecurityLevel(
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
            WidevineEvidenceReader.measureSecurityLevel(
                openSession = { throw ResourceBusyException("busy") },
                closeSession = {},
                provision = {}
            )
        }
    }
}
