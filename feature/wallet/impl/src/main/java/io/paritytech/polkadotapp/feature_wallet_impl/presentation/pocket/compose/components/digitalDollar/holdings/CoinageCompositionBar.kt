package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.components.digitalDollar.holdings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.models.CoinageCompositionUiModel

/**
 * The balance partition drawn to scale: Spendable, Gaining privacy, Unavailable, left to right.
 *
 * The three segments are the same three buckets as the figures above, in the same order and with the same
 * three fills as the legend swatches — which is why the bar carries no labels of its own. Widths are
 * value-weighted shares of the total, and the last segment absorbs the rounding remainder so the fills
 * always meet the frame exactly. With nothing held, only the capsule frame is drawn.
 *
 * The caller sizes it: the modifier passed in is used as given rather than appended to.
 */
@Composable
internal fun CoinageCompositionBar(
    modifier: Modifier = Modifier,
    composition: CoinageCompositionUiModel,
    stripePhase: State<Float>,
    colors: HoldingColors,
) {
    Box(modifier = modifier) {
        PolkadotSurface(
            modifier = Modifier
                .padding(vertical = HoldingGeometry.summaryBarVerticalPadding)
                .fillMaxSize(),
            shape = RoundedCornerShape(percent = CAPSULE_PERCENT),
            // The segments are the fill; the surface contributes the frame and the clip that keeps the
            // segments inside it. Transparent is also exactly right for the empty state.
            color = Color.Transparent,
            border = BorderStroke(HoldingGeometry.frameWidth, colors.frame)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val spendableRight = composition.spendableFraction * size.width
                        val gainingPrivacyRight = spendableRight + composition.gainingPrivacyFraction * size.width
                        val period = HoldingGeometry.stripeWidth.toPx() * STRIPE_PERIODS_PER_WIDTH

                        onDrawBehind {
                            if (composition.isEmpty) return@onDrawBehind

                            drawRect(
                                color = colors.spendable,
                                topLeft = Offset.Zero,
                                size = Size(spendableRight, size.height)
                            )

                            drawBarberPole(
                                left = spendableRight,
                                top = 0f,
                                width = gainingPrivacyRight - spendableRight,
                                height = size.height,
                                cornerRadius = 0f,
                                stripePeriod = period,
                                phase = stripePhase.value,
                                colors = colors
                            )

                            // Runs to the far edge rather than to its own fraction: three floats need not
                            // sum to exactly one, and a hairline of ground showing through at the end would
                            // read as a fourth segment.
                            drawRect(
                                color = colors.notSpendable,
                                topLeft = Offset(gainingPrivacyRight, 0f),
                                size = Size(size.width - gainingPrivacyRight, size.height)
                            )
                        }
                    }
            )
        }
    }
}

/**
 * One legend swatch, drawn with the same three fills as the bar's segments so the two cannot drift.
 *
 * [gainingPrivacy] gets the live barber pole rather than a still of it, for the same reason.
 */
@Composable
internal fun CoinageLegendSwatch(
    fill: Color?,
    gainingPrivacy: Boolean,
    stripePhase: State<Float>,
    colors: HoldingColors,
) {
    Box(
        modifier = Modifier
            .size(HoldingGeometry.legendSwatch)
            .drawWithCache {
                val corner = HoldingGeometry.legendSwatchCorner.toPx()
                val frame = HoldingGeometry.frameWidth.toPx()
                val period = HoldingGeometry.stripeWidth.toPx() * STRIPE_PERIODS_PER_WIDTH

                onDrawBehind {
                    if (gainingPrivacy) {
                        drawBarberPole(
                            left = 0f,
                            top = 0f,
                            width = size.width,
                            height = size.height,
                            cornerRadius = corner,
                            stripePeriod = period,
                            phase = stripePhase.value,
                            colors = colors
                        )
                    }

                    drawFramedBar(
                        left = 0f,
                        top = 0f,
                        width = size.width,
                        height = size.height,
                        fill = if (gainingPrivacy) Color.Transparent else fill ?: Color.Transparent,
                        frame = colors.frame,
                        frameWidth = frame,
                        cornerRadius = corner
                    )
                }
            }
    )
}

@Preview
@Composable
private fun CoinageCompositionBarPreview() {
    PolkadotTheme {
        CoinageCompositionBar(
            modifier = Modifier.height(HoldingGeometry.summaryBarHeight),
            composition = CoinageCompositionUiModel(
                spendableFraction = 0.5f,
                gainingPrivacyFraction = 0.3f,
                unavailableFraction = 0.2f
            ),
            stripePhase = rememberBarberPolePhase(),
            colors = rememberHoldingColors()
        )
    }
}

private const val CAPSULE_PERCENT = 50
private const val STRIPE_PERIODS_PER_WIDTH = 2f
