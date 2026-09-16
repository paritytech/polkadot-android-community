package io.paritytech.polkadotapp.feature_connection_status_api.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.designsystem.colors.PolkadotColorsPalette
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainGlyph
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicatorsModel
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthItemModel
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class ChainHealthIndicatorsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aFullShareIsAFilledDisc() =
        renderAndAssert("share-full", producing(1f), FULL_RING, null) { it.fg.primary }

    @Test
    fun notProducingBlocksIsAThreeQuarterRing() =
        renderAndAssert("not-producing", ChainHealthIndicator.Outage, THREE_QUARTER_MIN, THREE_QUARTER_MAX) { it.stroke.secondary }

    @Test
    fun aThreeQuarterShareFillsThreeQuartersOfTheRing() =
        renderAndAssert("share-075", producing(0.75f), minimumFor(0.75f), maximumFor(0.75f)) { it.fg.primary }

    @Test
    fun aHalfShareFillsHalfTheRing() =
        renderAndAssert("share-050", producing(0.5f), minimumFor(0.5f), maximumFor(0.5f)) { it.fg.primary }

    @Test
    fun aWarningShareDrawsInWarning() =
        renderAndAssert("share-040", producing(0.4f), minimumFor(0.4f), maximumFor(0.4f)) { it.fg.warning }

    @Test
    fun anErrorShareDrawsInError() =
        renderAndAssert("share-020", producing(0.2f), minimumFor(0.2f), maximumFor(0.2f)) { it.fg.error }

    @Test
    fun connectingIsAColourlessRing() =
        renderAndAssert("connecting", ChainHealthIndicator.Connecting, FULL_RING, null) { it.stroke.secondary }

    @Test
    fun brokenIsADottedRing() =
        renderAndAssert("broken", ChainHealthIndicator.Broken, DOTTED_MIN, DOTTED_MAX) { it.stroke.secondary }

    @Test
    fun noInternetCarriesTheSameMarkAsBroken() =
        renderAndAssert("no-internet", ChainHealthIndicator.NoInternet, DOTTED_MIN, DOTTED_MAX) { it.stroke.secondary }

    // The panel draws the same states larger; a size not threaded through one drawing shows up here as a
    // ring band sampled at the wrong radius.
    @Test
    fun panelSizeKeepsTheArcAtItsShare() =
        renderAndAssert(
            "share-050-panel",
            producing(0.5f),
            minimumFor(0.5f),
            maximumFor(0.5f),
            indicatorSize = ChainIndicatorSize.Panel,
        ) { it.fg.primary }

    @Test
    fun notProducingBlocksDrawsTheCrossInTheRingGap() {
        var expected = Color.Unspecified
        var ring = Color.Unspecified
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                PolkadotTheme {
                    expected = PolkadotTheme.colors.fg.disabled
                    ring = PolkadotTheme.colors.stroke.secondary
                    Box(
                        modifier = Modifier
                            .background(PolkadotTheme.colors.bg.surface.main)
                            .padding(16.dp),
                    ) {
                        ChainHealthIndicators(modifier = Modifier.testTag(TAG), model = model(ChainHealthIndicator.Outage))
                    }
                }
            }
        }

        val image = compose.onNodeWithTag(TAG).captureToImage()
        save(image, "not-producing-cross")
        val pixels = image.toPixelMap()
        val size = image.height
        val offset = (size * RING_BAND_RADIUS / SQRT_TWO).roundToInt()
        val x = size / 2 - offset
        val y = size / 2 - offset
        assertTrue("the cross should sit on the ring at the upper left", pixels[x, y].isClose(expected))
        assertTrue("the cross must not be the ring colour", !pixels[x, y].isClose(ring))
    }

    private fun producing(share: Float) = ChainHealthIndicator.producing(share, BLOCK_TIME)

    private fun renderAndAssert(
        name: String,
        indicator: ChainHealthIndicator,
        minimumShare: Float,
        maximumShare: Float?,
        indicatorSize: ChainIndicatorSize = ChainIndicatorSize.Bar,
        surround: (PolkadotColorsPalette) -> Color,
    ) {
        var expected = Color.Unspecified
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                PolkadotTheme {
                    expected = surround(PolkadotTheme.colors)
                    Box(
                        modifier = Modifier
                            .background(PolkadotTheme.colors.bg.surface.main)
                            .padding(16.dp),
                    ) {
                        ChainHealthIndicators(
                            modifier = Modifier.testTag(TAG),
                            model = model(indicator),
                            indicatorSize = indicatorSize,
                        )
                    }
                }
            }
        }

        val image = compose.onNodeWithTag(TAG).captureToImage()
        save(image, name)
        val share = surroundShare(image, expected)
        assertTrue("$name: the surround covers ${(share * 100).roundToInt()}% of the ring band", share >= minimumShare)
        if (maximumShare != null) {
            assertTrue("$name: the surround covers ${(share * 100).roundToInt()}%, over this share's slice", share < maximumShare)
        }
    }

    private fun model(indicator: ChainHealthIndicator) = ChainHealthIndicatorsModel(
        persistentListOf(
            item("people", ChainGlyph.People, indicator),
            item("hub", ChainGlyph.AssetHub, indicator),
            item("bulletin", ChainGlyph.Bulletin, indicator),
        ),
    )

    private fun item(name: String, glyph: ChainGlyph, indicator: ChainHealthIndicator) = ChainHealthItemModel(
        chainName = name,
        glyph = glyph,
        indicator = indicator,
    )

    private fun surroundShare(image: ImageBitmap, expected: Color): Float {
        val pixels = image.toPixelMap()
        val size = image.height
        val center = size / 2f
        val radius = size * RING_BAND_RADIUS
        val matches = (0 until SAMPLES).count { step ->
            val angle = 2 * Math.PI * step / SAMPLES
            val x = (center + radius * cos(angle)).toInt().coerceIn(0, image.width - 1)
            val y = (center + radius * sin(angle)).toInt().coerceIn(0, image.height - 1)
            pixels[x, y].isClose(expected)
        }
        return matches.toFloat() / SAMPLES
    }

    private fun Color.isClose(other: Color): Boolean =
        abs(red - other.red) < CHANNEL_TOLERANCE &&
            abs(green - other.green) < CHANNEL_TOLERANCE &&
            abs(blue - other.blue) < CHANNEL_TOLERANCE

    private fun save(image: ImageBitmap, name: String) {
        val dir = InstrumentationRegistry.getArguments().getString(OUTPUT_DIR_ARGUMENT)?.let(::File) ?: return
        dir.mkdirs()
        File(dir, "indicator-$name.png").outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // Round caps run the drawn arc past the path's own ends, so a sweep of the ring band reads a little long.
    private fun minimumFor(share: Float): Float = share - MIN_SLACK

    private fun maximumFor(share: Float): Float = share + MAX_SLACK

    private companion object {
        const val TAG = "indicators"
        const val OUTPUT_DIR_ARGUMENT = "additionalTestOutputDir"
        const val SAMPLES = 72
        val SQRT_TWO = sqrt(2f)
        val BLOCK_TIME: Duration = 2.seconds
        const val RING_BAND_RADIUS = 0.45f
        // Must stay under the 0.098 that separates stroke.secondary from fg.disabled, or the two read as one.
        const val CHANNEL_TOLERANCE = 0.06f
        const val FULL_RING = 0.9f
        const val MIN_SLACK = 0.07f
        const val MAX_SLACK = 0.11f
        // The broken ring is dashed, so a ring-band sweep only ever lands on part of it.
        const val DOTTED_MIN = 0.35f
        const val DOTTED_MAX = 0.85f
        // The design leaves the ring open at a fixed 270 degrees rather than reporting how far production fell.
        const val THREE_QUARTER_MIN = 0.68f
        const val THREE_QUARTER_MAX = 0.86f
    }
}
