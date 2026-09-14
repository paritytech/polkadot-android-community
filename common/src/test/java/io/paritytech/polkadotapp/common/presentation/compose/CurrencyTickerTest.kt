package io.paritytech.polkadotapp.common.presentation.compose

import org.junit.Assert.assertEquals
import org.junit.Test

private const val TICKER = "CASH"

class CurrencyTickerTest {
    @Test
    fun `should match a ticker standing on its own`() {
        assertRanges("CASH", listOf(0 until 4))
        assertRanges("12.34 CASH", listOf(6 until 10))
        assertRanges("Send CASH now", listOf(5 until 9))
        assertRanges("CASH.", listOf(0 until 4))
        assertRanges("(CASH)", listOf(1 until 5))
    }

    @Test
    fun `should match every occurrence`() {
        assertRanges("1 CASH = 1 CASH", listOf(2 until 6, 11 until 15))
    }

    @Test
    fun `should not match a ticker inside a longer word`() {
        assertRanges("CASHIER", emptyList())
        assertRanges("1CASH", emptyList())
        assertRanges("CASHCASH", emptyList())
        assertRanges("Buy CASHBACK today", emptyList())
    }

    @Test
    fun `should return nothing when the ticker is absent or empty`() {
        assertRanges("no ticker here", emptyList())
        assertEquals(emptyList<IntRange>(), tickerRanges("CASH", ticker = ""))
    }

    private fun assertRanges(source: String, expected: List<IntRange>) {
        assertEquals(expected, tickerRanges(source, TICKER))
    }
}
