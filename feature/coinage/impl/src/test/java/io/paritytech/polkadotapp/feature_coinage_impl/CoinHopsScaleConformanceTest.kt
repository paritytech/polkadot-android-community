package io.paritytech.polkadotapp.feature_coinage_impl

import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Hop
import io.paritytech.polkadotapp.feature_coinage_impl.data.mappers.decodeCoinHops
import io.paritytech.polkadotapp.feature_coinage_impl.data.mappers.encodeCoinHops
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the on-disk encoding of `coins.hops`.
 *
 * These hex strings are what every already-stored row is written in, so they must never be edited. A change
 * that makes this test fail is a change that corrupts existing coins: freeze the old shape as
 * `CoinHopsLocalV60`, add a migration, and add a new test beside this one.
 */
class CoinHopsScaleConformanceTest {
    @Test
    fun `no hops encodes as an empty vector`() {
        assertEquals(EMPTY_HOPS, emptyList<Hop>().encodeCoinHops().toHex())
    }

    @Test
    fun `hops encode as a vector of indexed variants`() {
        assertEquals(MIXED_HOPS, mixedHops.encodeCoinHops().toHex())
    }

    @Test
    fun `stored hops decode back to what was written`() {
        assertEquals(mixedHops, MIXED_HOPS.fromHex().decodeCoinHops())
        assertEquals(emptyList<Hop>(), EMPTY_HOPS.fromHex().decodeCoinHops())
    }

    /** A column that predates the hops blob, and a row whose blob failed to write, are both "no hops". */
    @Test
    fun `an absent or unreadable blob decodes as no hops`() {
        assertEquals(emptyList<Hop>(), null.decodeCoinHops())
        assertEquals(emptyList<Hop>(), byteArrayOf().decodeCoinHops())
        assertEquals(emptyList<Hop>(), byteArrayOf(0x7f, 0x7f, 0x7f).decodeCoinHops())
    }

    private val mixedHops = listOf(Hop.Transfer(bundleSize = 1), Hop.Split(fanout = 3), Hop.Transfer(bundleSize = 255))

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private fun String.fromHex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private companion object {
        const val EMPTY_HOPS = "00"
        const val MIXED_HOPS = "0c0001000000010300000000ff000000"
    }
}
