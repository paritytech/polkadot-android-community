package io.paritytech.polkadotapp.common.presentation.compose

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import io.paritytech.polkadotapp.designsystem.typography.PolkadotFontFamilies
import org.junit.Assert.assertEquals
import org.junit.Test

private const val TICKER = "CASH"

class CurrencyTickerTest {
    @Test
    fun `should match a ticker standing on its own`() {
        assertRanges("CASH", listOf(TextRange(0, 4)))
        assertRanges("12.34 CASH", listOf(TextRange(6, 10)))
        assertRanges("Send CASH now", listOf(TextRange(5, 9)))
        assertRanges("CASH.", listOf(TextRange(0, 4)))
        assertRanges("(CASH)", listOf(TextRange(1, 5)))
    }

    @Test
    fun `should match every occurrence`() {
        assertRanges("1 CASH = 1 CASH", listOf(TextRange(2, 6), TextRange(11, 15)))
    }

    @Test
    fun `should not match a ticker inside a longer word`() {
        assertRanges("CASHIER", emptyList())
        assertRanges("1CASH", emptyList())
        assertRanges("CASH1", emptyList())
        assertRanges("xCASH", emptyList())
        assertRanges("CASHCASH", emptyList())
        assertRanges("Buy CASHBACK today", emptyList())
    }

    @Test(timeout = 5_000)
    fun `should return nothing when the ticker is absent or empty`() {
        assertRanges("no ticker here", emptyList())
        assertEquals(emptyList<TextRange>(), tickerRanges("CASH", ticker = ""))
    }

    @Test
    fun `should replace a standalone ticker with one logo glyph`() {
        val logoText = replaceTickerWithLogo("12.34 CASH", TICKER)

        assertEquals("12.34 C", logoText.text)
        assertEquals(listOf(TextRange(6, 7)), logoText.logoRanges())
        assertEquals(PolkadotFontFamilies.cashLogo, logoText.spanStyles.single().item.fontFamily)
    }

    @Test
    fun `should replace every standalone ticker`() {
        assertLogo(source = "1 CASH = 1 CASH", text = "1 C = 1 C", ranges = listOf(TextRange(2, 3), TextRange(8, 9)))
        assertLogo(source = "CASH", text = "C", ranges = listOf(TextRange(0, 1)))
    }

    @Test
    fun `should leave a ticker inside a longer word untouched`() {
        assertLogo(source = "Buy CASHBACK today", text = "Buy CASHBACK today", ranges = emptyList())
    }

    private fun assertRanges(source: String, expected: List<TextRange>) {
        assertEquals(expected, tickerRanges(source, TICKER))
    }

    private fun assertLogo(source: String, text: String, ranges: List<TextRange>) {
        val logoText = replaceTickerWithLogo(source, TICKER)

        assertEquals(text, logoText.text)
        assertEquals(ranges, logoText.logoRanges())
    }

    private fun AnnotatedString.logoRanges() = spanStyles.map { TextRange(it.start, it.end) }
}
