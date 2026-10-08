package io.paritytech.polkadotapp.common.presentation.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.paritytech.polkadotapp.common.presentation.paymentAsset.LocalPaymentAssetBrand
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.designsystem.typography.PolkadotCashLogo
import io.paritytech.polkadotapp.designsystem.typography.PolkadotFontFamilies

@Composable
fun String.withCurrencyTickerStyle(
    style: TextStyle,
    logo: PolkadotCashLogo = PolkadotCashLogo.SmallCaps
): AnnotatedString = remember(this) { AnnotatedString(this) }.withCurrencyTickerStyle(style, logo)

@Composable
fun AnnotatedString.withCurrencyTickerStyle(
    style: TextStyle,
    logo: PolkadotCashLogo = PolkadotCashLogo.SmallCaps
): AnnotatedString {
    val ticker = LocalPaymentAssetBrand.current.symbol
    val spanStyle = rememberTickerSpanStyle()

    return remember(this, ticker, spanStyle, logo) {
        if (ticker == CASH_LOGO_TICKER) {
            drawTickerAsLogo(this, logo)
        } else {
            buildAnnotatedString {
                append(this@withCurrencyTickerStyle)
                styleTickerOccurrences(this@withCurrencyTickerStyle.text, ticker, spanStyle)
            }
        }
    }
}

internal fun tickerRanges(source: String, ticker: String): List<TextRange> {
    if (ticker.isEmpty()) return emptyList()

    val ranges = mutableListOf<TextRange>()
    var start = source.indexOf(ticker)

    while (start >= 0) {
        val end = start + ticker.length
        if (source.standsAlone(start, end)) ranges += TextRange(start, end)
        start = source.indexOf(ticker, end)
    }

    return ranges
}

@Composable
private fun rememberTickerSpanStyle(): SpanStyle {
    val smallCaps = PolkadotTheme.typography.smallCaps.headlineMedium

    return remember(smallCaps) {
        // Only the face is taken: the ticker keeps the size of the text it sits in.
        SpanStyle(
            fontFamily = smallCaps.fontFamily,
            fontWeight = smallCaps.fontWeight,
            fontFeatureSettings = smallCaps.fontFeatureSettings
        )
    }
}

private fun AnnotatedString.Builder.styleTickerOccurrences(source: String, ticker: String, spanStyle: SpanStyle) {
    tickerRanges(source, ticker).forEach { range ->
        addStyle(style = spanStyle, start = range.start, end = range.end)
    }
}

// The ticker takes the glyph's case, which picks the wordmark; only its first letter is drawn, the rest stays for screen readers.
internal fun drawTickerAsLogo(source: AnnotatedString, logo: PolkadotCashLogo): AnnotatedString = buildAnnotatedString {
    var cursor = 0

    tickerRanges(source.text, CASH_LOGO_TICKER).forEach { range ->
        append(source.subSequence(cursor, range.start))

        val ticker = source.subSequence(range.start, range.end)
        val logoStart = length
        append(AnnotatedString(ticker.text.inCaseOf(logo.glyph), ticker.spanStyles))
        addStyle(CashLogoSpanStyle, logoStart, logoStart + 1)
        addStyle(CollapsedTickerSpanStyle, logoStart + 1, length)

        cursor = range.end
    }

    append(source.subSequence(cursor, source.length))
}

private fun String.inCaseOf(glyph: String): String = if (glyph.first().isLowerCase()) lowercase() else uppercase()

private const val CASH_LOGO_TICKER = "CASH"

private val CashLogoSpanStyle = SpanStyle(
    fontFamily = PolkadotFontFamilies.cashLogo,
    fontWeight = FontWeight.Normal,
    fontStyle = FontStyle.Normal,
    fontSynthesis = FontSynthesis.None
)

private val CollapsedTickerSpanStyle = SpanStyle(fontSize = 0.sp)

private fun String.standsAlone(start: Int, end: Int): Boolean =
    getOrNull(start - 1)?.isLetterOrDigit() != true && getOrNull(end)?.isLetterOrDigit() != true
