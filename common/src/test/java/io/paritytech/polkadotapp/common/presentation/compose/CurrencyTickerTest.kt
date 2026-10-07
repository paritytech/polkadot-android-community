package io.paritytech.polkadotapp.common.presentation.compose

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import io.paritytech.polkadotapp.designsystem.typography.PolkadotFontFamilies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun `should draw a standalone ticker as one logo glyph and keep its text`() {
        val logoText = drawTickerAsLogo(AnnotatedString("12.34 CASH"))

        assertEquals("12.34 CASH", logoText.text)
        assertEquals(listOf(TextRange(6, 7)), logoText.logoRanges())
        assertEquals(listOf(TextRange(7, 10)), logoText.collapsedRanges())
    }

    @Test
    fun `should draw every standalone ticker as a logo`() {
        assertLogo(source = "1 CASH = 1 CASH", logo = listOf(TextRange(2, 3), TextRange(11, 12)))
        assertLogo(source = "CASH", logo = listOf(TextRange(0, 1)))
    }

    @Test
    fun `should leave a ticker inside a longer word untouched`() {
        assertLogo(source = "Buy CASHBACK today", logo = emptyList())
    }

    @Test
    fun `should draw the logo at its regular weight inside bold text`() {
        val source = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("2 CASH") }
        }

        val styles = drawTickerAsLogo(source).spanStyles
        val logo = styles.single { it.item.fontFamily == PolkadotFontFamilies.cashLogo }

        assertEquals(FontWeight.Normal, logo.item.fontWeight)
        assertEquals(FontSynthesis.None, logo.item.fontSynthesis)
        assertTrue(styles.indexOf(logo) > styles.indexOfFirst { it.item.fontWeight == FontWeight.Bold })
    }

    private fun assertRanges(source: String, expected: List<TextRange>) {
        assertEquals(expected, tickerRanges(source, TICKER))
    }

    private fun assertLogo(source: String, logo: List<TextRange>) {
        val logoText = drawTickerAsLogo(AnnotatedString(source))

        assertEquals(source, logoText.text)
        assertEquals(logo, logoText.logoRanges())
    }

    private fun AnnotatedString.logoRanges() = spanStyles
        .filter { it.item.fontFamily == PolkadotFontFamilies.cashLogo }
        .map { TextRange(it.start, it.end) }

    private fun AnnotatedString.collapsedRanges() = spanStyles
        .filter { it.item.fontSize == 0.sp }
        .map { TextRange(it.start, it.end) }
}
