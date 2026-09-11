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
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator.Speed
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicatorsModel
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthItemModel
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class ChainHealthIndicatorsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun healthyIsAFilledDisc() = renderAndAssert("healthy", ChainHealthIndicator.Healthy, FULL_RING) { it.fg.primary }

    @Test
    fun outageIsAPartialErrorArc() =
        renderAndAssert("outage", ChainHealthIndicator.Outage(recentBlocks = 3, expectedBlocks = 5), PARTIAL_ARC) { it.fg.error }

    @Test
    fun goodSpeedIsAFullColourlessRing() =
        renderAndAssert("speed-good", ChainHealthIndicator.ConnectionSpeed(Speed.Good), FULL_RING) { it.fg.primary }

    @Test
    fun fairSpeedIsAFullWarningRing() =
        renderAndAssert("speed-fair", ChainHealthIndicator.ConnectionSpeed(Speed.Fair), FULL_RING) { it.fg.warning }

    @Test
    fun lowSpeedIsAFullErrorRing() =
        renderAndAssert("speed-low", ChainHealthIndicator.ConnectionSpeed(Speed.Low), FULL_RING) { it.fg.error }

    @Test
    fun connectingIsAColourlessRing() = renderAndAssert("connecting", ChainHealthIndicator.Connecting, FULL_RING) { it.stroke.secondary }

    @Test
    fun disconnectedIsAColourlessRing() =
        renderAndAssert("disconnected", ChainHealthIndicator.Disconnected, FULL_RING) { it.stroke.secondary }

    private fun renderAndAssert(
        name: String,
        indicator: ChainHealthIndicator,
        minimumShare: Float,
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
                        )
                    }
                }
            }
        }

        val image = compose.onNodeWithTag(TAG).captureToImage()
        save(image, name)
        val share = surroundShare(image, expected)
        assertTrue("$name: the surround colour covers ${(share * 100).roundToInt()}% of the ring band", share >= minimumShare)
    }

    private fun model(indicator: ChainHealthIndicator) = ChainHealthIndicatorsModel(
        persistentListOf(
            item("people", ChainGlyph.People, indicator),
            item("hub", ChainGlyph.AssetHub, indicator),
            item("bulletin", ChainGlyph.Bulletin, indicator),
        ),
    )

    private fun item(id: String, glyph: ChainGlyph, indicator: ChainHealthIndicator) = ChainHealthItemModel(
        chainId = id,
        chainName = id,
        glyph = glyph,
        indicator = indicator,
        expectedBlockTime = 6.seconds,
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

    private companion object {
        const val TAG = "indicators"
        const val OUTPUT_DIR_ARGUMENT = "additionalTestOutputDir"
        const val SAMPLES = 72
        const val RING_BAND_RADIUS = 0.45f
        const val CHANNEL_TOLERANCE = 0.12f
        const val FULL_RING = 0.9f
        const val PARTIAL_ARC = 0.5f
    }
}
