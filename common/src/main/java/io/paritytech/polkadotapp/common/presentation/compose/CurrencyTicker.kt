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
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.paritytech.polkadotapp.common.presentation.paymentAsset.LocalPaymentAssetBrand
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.designsystem.typography.PolkadotFontFamilies

@Composable
fun String.withCurrencyTickerStyle(style: TextStyle): AnnotatedString =
    remember(this) { AnnotatedString(this) }.withCurrencyTickerStyle(style)

@Composable
fun AnnotatedString.withCurrencyTickerStyle(style: TextStyle): AnnotatedString {
    val ticker = LocalPaymentAssetBrand.current.symbol
    val spanStyle = rememberTickerSpanStyle()

    return remember(this, ticker, spanStyle) {
        if (ticker == CASH_LOGO_TICKER) {
            drawTickerAsLogo(this)
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

// One uppercase letter in the CashLogo font draws the whole wordmark; the rest stays in the text for screen readers.
internal fun drawTickerAsLogo(source: AnnotatedString): AnnotatedString = buildAnnotatedString {
    append(source)

    tickerRanges(source.text, CASH_LOGO_TICKER).forEach { range ->
        addStyle(CashLogoSpanStyle, range.start, range.start + 1)
        addStyle(CollapsedTickerSpanStyle, range.start + 1, range.end)
    }
}

private const val CASH_LOGO_TICKER = "CASH"

private val CashLogoFontSize = 1.25.em

private val CashLogoSpanStyle = SpanStyle(
    fontFamily = PolkadotFontFamilies.cashLogo,
    fontSize = CashLogoFontSize,
    fontWeight = FontWeight.Normal,
    fontStyle = FontStyle.Normal,
    fontSynthesis = FontSynthesis.None
)

private val CollapsedTickerSpanStyle = SpanStyle(fontSize = 0.sp)

private fun String.standsAlone(start: Int, end: Int): Boolean =
    getOrNull(start - 1)?.isLetterOrDigit() != true && getOrNull(end)?.isLetterOrDigit() != true
