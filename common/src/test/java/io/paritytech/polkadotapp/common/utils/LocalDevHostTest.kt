package io.paritytech.polkadotapp.common.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalDevHostTest {
    @Test
    fun `reads a bare host and port as http`() {
        assertEquals("http://localhost:5173", LocalDevHost.parseOrigin("localhost:5173"))
    }

    @Test
    fun `keeps an explicit http scheme`() {
        assertEquals("http://localhost:5173", LocalDevHost.parseOrigin("http://localhost:5173"))
    }

    @Test
    fun `reduces a full url to its origin`() {
        assertEquals("http://localhost:5173", LocalDevHost.parseOrigin("http://localhost:5173/apps?id=1#k=2"))
    }

    @Test
    fun `accepts a host without a port`() {
        assertEquals("http://localhost", LocalDevHost.parseOrigin("localhost"))
    }

    @Test
    fun `accepts the loopback address and the emulator's host alias`() {
        assertEquals("http://127.0.0.1:3000", LocalDevHost.parseOrigin("127.0.0.1:3000"))
        assertEquals("http://10.0.2.2:8080", LocalDevHost.parseOrigin("10.0.2.2:8080"))
    }

    @Test
    fun `rejects https, since a dev server is served in the clear`() {
        assertNull(LocalDevHost.parseOrigin("https://localhost:5173"))
    }

    @Test
    fun `rejects a host that is not local`() {
        assertNull(LocalDevHost.parseOrigin("coinflip.dot"))
        assertNull(LocalDevHost.parseOrigin("http://example.com:5173"))
    }

    @Test
    fun `does not treat a host merely containing a local name as local`() {
        assertNull(LocalDevHost.parseOrigin("localhost.example.com:5173"))
        assertNull(LocalDevHost.parseOrigin("notlocalhost:5173"))
    }

    @Test
    fun `rejects blank input`() {
        assertNull(LocalDevHost.parseOrigin(""))
        assertNull(LocalDevHost.parseOrigin("   "))
    }

    @Test
    fun `is case-insensitive about the scheme and host`() {
        assertEquals("http://localhost:5173", LocalDevHost.parseOrigin("HTTP://LocalHost:5173"))
    }
}
