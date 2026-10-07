package io.paritytech.polkadotapp.common.presentation.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import io.paritytech.polkadotapp.common.presentation.paymentAsset.LocalPaymentAssetBrand
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.designsystem.typography.PolkadotFontFamilies

@Composable
fun String.withCurrencyTickerStyle(style: TextStyle): AnnotatedString = withSmallCapsTicker()

@Composable
fun AnnotatedString.withCurrencyTickerStyle(style: TextStyle): AnnotatedString {
    val ticker = LocalPaymentAssetBrand.current.symbol
    val spanStyle = rememberTickerSpanStyle()

    return remember(this, ticker, spanStyle) {
        buildAnnotatedString {
            append(this@withCurrencyTickerStyle)
            styleTickerOccurrences(this@withCurrencyTickerStyle.text, ticker, spanStyle)
        }
    }
}

@Composable
fun String.withCurrencyLogoTicker(): AnnotatedString {
    val ticker = LocalPaymentAssetBrand.current.symbol

    return if (ticker == CASH_LOGO_TICKER) {
        remember(this) { replaceTickerWithLogo(this, ticker) }
    } else {
        withSmallCapsTicker()
    }
}

@Composable
private fun String.withSmallCapsTicker(): AnnotatedString {
    val ticker = LocalPaymentAssetBrand.current.symbol
    val spanStyle = rememberTickerSpanStyle()

    return remember(this, ticker, spanStyle) {
        buildAnnotatedString {
            append(this@withSmallCapsTicker)
            styleTickerOccurrences(this@withSmallCapsTicker, ticker, spanStyle)
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

internal fun replaceTickerWithLogo(source: String, ticker: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0

    tickerRanges(source, ticker).forEach { range ->
        append(source.substring(cursor, range.start))
        withStyle(CashLogoSpanStyle) { append(CASH_LOGO_GLYPH) }
        cursor = range.end
    }

    append(source.substring(cursor))
}

private const val CASH_LOGO_TICKER = "CASH"

// Every uppercase letter and digit in the CashLogo font draws the whole wordmark, so one character is enough.
private const val CASH_LOGO_GLYPH = "C"

private val CashLogoFontSize = 1.25.em

private val CashLogoSpanStyle = SpanStyle(
    fontFamily = PolkadotFontFamilies.cashLogo,
    fontSize = CashLogoFontSize
)

private fun String.standsAlone(start: Int, end: Int): Boolean =
    getOrNull(start - 1)?.isLetterOrDigit() != true && getOrNull(end)?.isLetterOrDigit() != true
