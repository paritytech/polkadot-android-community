package io.paritytech.polkadotapp.feature_settings_impl.presentation.main.components.privacyMode

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.paritytech.polkadotapp.design.theme.PolkadotTheme

// The recessed groove the modes sit in, carrying the continuous speed-to-privacy scale as tick marks.
// Drawn rather than composed: an inner shadow has no Compose primitive, and `Modifier.blur` is API 31+ while
// the app ships from 29 — so the recess is a blurred stroke clipped to the groove, the same `BlurMaskFilter`
// approach the design system already uses for blurred mnemonics.
@Composable
internal fun PrivacyModeTrack(modifier: Modifier) {
    // One level below the card that hosts it, not the page background: the groove is a recess in the card,
    // and dropping it all the way to `main` reads as a hole punched through to the screen behind.
    val troughColor = PolkadotTheme.colors.bg.surface.nested
    val rimColor = PolkadotTheme.colors.stroke.secondary
    val recessColor = PolkadotTheme.colors.avatar.bg.onyx

    val scaleColors = listOf(
        PolkadotTheme.colors.bg.status.warning,
        PolkadotTheme.colors.bg.status.success,
        PolkadotTheme.colors.avatar.bg.sapphire,
        PolkadotTheme.colors.avatar.bg.amethyst
    )

    Spacer(
        modifier = Modifier.drawWithCache {
            val cornerRadius = size.height / 2f
            val trough = Path().apply {
                addRoundRect(RoundRect(size.toRect(), CornerRadius(cornerRadius)))
            }

            // The scale spans centre-to-centre of the outer modes, so its ends disappear under them rather
            // than stopping short of the groove's rounded caps.
            val scaleStart = TRACK_INSET.toPx()
            val scaleEnd = size.width - TRACK_INSET.toPx()
            val ticks = buildTicks(
                startX = scaleStart,
                endX = scaleEnd,
                centreY = size.height / 2f,
                tickWidth = TICK_WIDTH.toPx(),
                tickHeight = TICK_HEIGHT.toPx(),
                step = TICK_STEP.toPx()
            )
            val scaleBrush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f to scaleColors[0],
                    SCALE_BALANCED_STOP to scaleColors[1],
                    SCALE_SAPPHIRE_STOP to scaleColors[2],
                    1f to scaleColors[3]
                ),
                startX = scaleStart,
                endX = scaleEnd
            )

            // The shadow the upper rim casts into the groove: darkest against that rim and gone about a
            // third of the way down. Measured off the design, and a gradient rather than a blurred outline
            // because a blur soft enough to read as a shadow also spreads the darkness away to nothing.
            val recessBrush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to recessColor.copy(alpha = RECESS_ALPHA),
                    RECESS_DEPTH to recessColor.copy(alpha = 0f)
                ),
                startY = 0f,
                endY = size.height
            )

            onDrawBehind {
                drawPath(trough, color = troughColor)
                drawPath(trough, brush = recessBrush)

                drawPath(ticks, brush = scaleBrush)
                drawPath(trough, color = rimColor, style = Stroke(width = RIM_STROKE.toPx()))
            }
        }.then(modifier)
    )
}

// One upright bar per step, laid left to right; the gradient brush colours them by position.
private fun buildTicks(
    startX: Float,
    endX: Float,
    centreY: Float,
    tickWidth: Float,
    tickHeight: Float,
    step: Float
): Path {
    val path = Path()
    val top = centreY - tickHeight / 2f
    var x = startX

    while (x + tickWidth <= endX) {
        path.addRect(Rect(offset = Offset(x, top), size = Size(tickWidth, tickHeight)))
        x += step
    }

    return path
}

// Half the selected circle, so the outer modes sit fully inside the groove.
internal val TRACK_INSET = 20.dp

internal val TRACK_HEIGHT = 40.dp

private val TICK_WIDTH = 2.dp
private val TICK_HEIGHT = 6.dp

// Also the haptic grain of a drag: the selector ticks once per mark the circle passes.
internal val TICK_STEP = 8.dp

private const val SCALE_BALANCED_STOP = 0.49f
private const val SCALE_SAPPHIRE_STOP = 0.73f

private const val RECESS_ALPHA = 0.7f

/** Share of the groove's height the rim shadow spans before it fades out. */
private const val RECESS_DEPTH = 0.3f

private val RIM_STROKE = 1.dp
