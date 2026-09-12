package io.paritytech.polkadotapp.feature_connection_status_api.presentation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.paritytech.polkadotapp.design.components.icon.NovaIcon
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.AssetHub
import io.paritytech.polkadotapp.design.components.icon.vectors.Bulletin
import io.paritytech.polkadotapp.design.components.icon.vectors.People
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainGlyph
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator.Speed
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicatorsModel
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthItemModel
import kotlinx.collections.immutable.persistentListOf
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.seconds

private const val PULSE_MIN_ALPHA = 0.3f
private const val PULSE_DURATION_MS = 900
private const val TOP_ANGLE = -90f
private const val GOOD_SWEEP = 270f
private const val FAIR_SWEEP = 180f
private const val LOW_SWEEP = 90f
private const val SCALLOP_LOBES = 11
private const val SCALLOP_TROUGH_RATIO = 0.965f
private const val STEPS_PER_LOBE = 12
private const val TWO_PI = 2 * PI.toFloat()
private const val DOT_COUNT = 16
private const val NOT_PRODUCING_SWEEP = 270f

// 225 degrees: the upper left, where the design leaves the ring open for the cross.
private const val CROSS_ANGLE_RADIANS = 1.25f * PI.toFloat()
private const val CROSS_ARM_RATIO = 0.118f
private val PREVIEW_BLOCK_TIME = 6.seconds

/**
 * The three lengths one indicator is drawn from. [Bar] is the top-bar and tab-bar size; [Panel] is the
 * same drawing at the size the "Network Status" rows use.
 */
@Immutable
data class ChainIndicatorSize(
    val diameter: Dp,
    val glyph: Dp,
    val ringStroke: Dp,
) {
    companion object {
        val Bar = ChainIndicatorSize(diameter = 20.dp, glyph = 10.dp, ringStroke = 2.dp)
        val Panel = ChainIndicatorSize(diameter = 35.dp, glyph = 16.dp, ringStroke = 3.5f.dp)
    }
}

object ChainHealthBarDefaults {
    /**
     * Height of the bar's content row, excluding the status-bar inset. The root also inflates the
     * content's top window inset by this amount so screens sit below the bar while their backgrounds
     * still draw full-bleed behind it.
     */
    val ContentHeight = ChainIndicatorSize.Bar.diameter
}

/**
 * The always-on chain-health bar, overlaid at the very top like the system status indicators.
 * Transparent, so whatever the screen draws behind it stays visible.
 */
@Composable
fun ChainHealthBar(model: ChainHealthIndicatorsModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(ChainHealthBarDefaults.ContentHeight)
            .padding(horizontal = PolkadotTheme.spacings.medium),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChainHealthIndicators(model = model)
    }
}

/**
 * One indicator per monitored chain. The inner glyph names the chain; the disc or ring around it draws
 * [ChainHealthIndicator]. Display-only — the row carries no press target of its own.
 */
@Composable
fun ChainHealthIndicators(
    modifier: Modifier = Modifier,
    model: ChainHealthIndicatorsModel,
    indicatorSize: ChainIndicatorSize = ChainIndicatorSize.Bar,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(PolkadotTheme.spacings.tiny),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            model.chains.forEach { item -> ChainIndicator(item = item, indicatorSize = indicatorSize) }
        }
    }
}

/** One chain's indicator on its own: the glyph inside the disc or ring for its [ChainHealthIndicator]. */
@Composable
fun ChainIndicator(
    modifier: Modifier = Modifier,
    item: ChainHealthItemModel,
    indicatorSize: ChainIndicatorSize,
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier.size(indicatorSize.diameter),
            contentAlignment = Alignment.Center,
        ) {
            when (val indicator = item.indicator) {
                ChainHealthIndicator.Healthy -> HealthyDisc(item, indicatorSize)
                is ChainHealthIndicator.Outage -> NotProducingRing(item, indicatorSize)
                is ChainHealthIndicator.ConnectionSpeed -> SpeedArc(item, indicator, indicatorSize)
                ChainHealthIndicator.Connecting -> ConnectingRing(item, indicatorSize)
                ChainHealthIndicator.Disconnected -> DottedRing(item, indicatorSize)
            }
        }
    }
}

@Composable
private fun HealthyDisc(item: ChainHealthItemModel, indicatorSize: ChainIndicatorSize) {
    PolkadotSurface(
        modifier = Modifier.fillMaxSize(),
        shape = CircleShape,
        color = PolkadotTheme.colors.fg.primary,
        contentAlignment = Alignment.Center,
    ) {
        Glyph(item = item, tint = PolkadotTheme.colors.fg.primaryInverted, indicatorSize = indicatorSize)
    }
}

@Composable
private fun NotProducingRing(item: ChainHealthItemModel, indicatorSize: ChainIndicatorSize) {
    val ringColor = PolkadotTheme.colors.stroke.secondary
    val crossColor = PolkadotTheme.colors.fg.disabled
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val stroke = indicatorSize.ringStroke.toPx()
                val style = Stroke(width = stroke, cap = StrokeCap.Round)
                val inset = Offset(stroke / 2, stroke / 2)
                val arcSize = Size(size.width - stroke, size.height - stroke)
                onDrawBehind {
                    drawArc(
                        color = ringColor,
                        startAngle = TOP_ANGLE,
                        sweepAngle = NOT_PRODUCING_SWEEP,
                        useCenter = false,
                        topLeft = inset,
                        size = arcSize,
                        style = style,
                    )
                }
            },
    )
    Glyph(item = item, tint = PolkadotTheme.colors.fg.disabled, indicatorSize = indicatorSize)
    // The design puts the cross in the ring's gap and above the glyph, so it paints last.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val stroke = indicatorSize.ringStroke.toPx()
                val radius = (size.minDimension - stroke) / 2
                val centre = Offset(size.width / 2, size.height / 2)
                val crossCentre = centre + Offset(
                    radius * cos(CROSS_ANGLE_RADIANS),
                    radius * sin(CROSS_ANGLE_RADIANS),
                )
                val arm = size.minDimension * CROSS_ARM_RATIO
                onDrawBehind {
                    drawLine(crossColor, crossCentre - Offset(arm, arm), crossCentre + Offset(arm, arm), stroke, StrokeCap.Round)
                    drawLine(crossColor, crossCentre + Offset(arm, -arm), crossCentre - Offset(arm, -arm), stroke, StrokeCap.Round)
                }
            },
    )
}

@Composable
private fun SpeedArc(item: ChainHealthItemModel, indicator: ChainHealthIndicator.ConnectionSpeed, indicatorSize: ChainIndicatorSize) {
    val trackColor = PolkadotTheme.colors.stroke.secondary
    val color = when (indicator.speed) {
        Speed.Good -> PolkadotTheme.colors.fg.primary
        Speed.Fair -> PolkadotTheme.colors.fg.warning
        Speed.Low -> PolkadotTheme.colors.fg.error
    }
    val sweepDegrees = when (indicator.speed) {
        Speed.Good -> GOOD_SWEEP
        Speed.Fair -> FAIR_SWEEP
        Speed.Low -> LOW_SWEEP
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val stroke = indicatorSize.ringStroke.toPx()
                val trackStyle = Stroke(stroke)
                val arcStyle = Stroke(width = stroke, cap = StrokeCap.Round)
                val radius = (size.minDimension - stroke) / 2
                val inset = Offset(stroke / 2, stroke / 2)
                val arcSize = Size(size.width - stroke, size.height - stroke)
                onDrawBehind {
                    drawCircle(color = trackColor, radius = radius, style = trackStyle)
                    drawArc(
                        color = color,
                        startAngle = TOP_ANGLE,
                        // The design fills the ring backwards from the top, so the sweep is negative.
                        sweepAngle = -sweepDegrees,
                        useCenter = false,
                        topLeft = inset,
                        size = arcSize,
                        style = arcStyle,
                    )
                }
            },
    )
    Glyph(item = item, tint = PolkadotTheme.colors.fg.primary, indicatorSize = indicatorSize)
}

@Composable
private fun ConnectingRing(item: ChainHealthItemModel, indicatorSize: ChainIndicatorSize) {
    ScallopRing(color = PolkadotTheme.colors.stroke.secondary, indicatorSize = indicatorSize)
    val pulse = rememberInfiniteTransition(label = "ChainConnectingPulse")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = PULSE_MIN_ALPHA,
        animationSpec = infiniteRepeatable(tween(PULSE_DURATION_MS), RepeatMode.Reverse),
        label = "ChainConnectingGlyphAlpha",
    )
    Box(modifier = Modifier.graphicsLayer { this.alpha = alpha }) {
        Glyph(item = item, tint = PolkadotTheme.colors.fg.primary, indicatorSize = indicatorSize)
    }
}

@Composable
private fun DottedRing(item: ChainHealthItemModel, indicatorSize: ChainIndicatorSize) {
    val ringColor = PolkadotTheme.colors.stroke.secondary
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val stroke = indicatorSize.ringStroke.toPx()
                val radius = (size.minDimension - stroke) / 2
                // Deriving the period from the circumference keeps the dots from bunching at the seam, and
                // the on-length is zero because a round cap already draws a dot one stroke wide at each end.
                val period = TWO_PI * radius / DOT_COUNT
                val style = Stroke(
                    width = stroke,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(0f, period)),
                )
                onDrawBehind { drawCircle(color = ringColor, radius = radius, style = style) }
            },
    )
    Glyph(item = item, tint = PolkadotTheme.colors.fg.disabled, indicatorSize = indicatorSize)
}

@Composable
private fun ScallopRing(color: Color, indicatorSize: ChainIndicatorSize) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val stroke = indicatorSize.ringStroke.toPx()
                val crest = (size.minDimension - stroke) / 2
                val centre = Offset(size.width / 2, size.height / 2)
                val path = scallopPath(centre, crest, crest * SCALLOP_TROUGH_RATIO)
                val style = Stroke(width = stroke)
                onDrawBehind { drawPath(path = path, color = color, style = style) }
            },
    )
}

// Polar cosine modulation: the radius rides between crest and trough once per lobe, so the ring ripples
// SCALLOP_LOBES times around. Sampled as a polyline because a cosine radius has no path-verb equivalent.
private fun scallopPath(centre: Offset, crest: Float, trough: Float): Path {
    val path = Path()
    val steps = SCALLOP_LOBES * STEPS_PER_LOBE
    for (step in 0..steps) {
        val angle = TWO_PI * step / steps
        val radius = trough + (crest - trough) * (1 + cos(SCALLOP_LOBES * angle)) / 2
        val x = centre.x + radius * cos(angle)
        val y = centre.y + radius * sin(angle)
        if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

@Composable
private fun Glyph(item: ChainHealthItemModel, tint: Color, indicatorSize: ChainIndicatorSize) {
    NovaIcon(
        modifier = Modifier.requiredSize(indicatorSize.glyph),
        imageVector = item.glyph.imageVector(),
        tint = tint,
        contentDescription = item.chainName,
    )
}

private fun ChainGlyph.imageVector(): ImageVector = when (this) {
    ChainGlyph.People -> NovaIcons.People
    ChainGlyph.AssetHub -> NovaIcons.AssetHub
    ChainGlyph.Bulletin -> NovaIcons.Bulletin
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ChainHealthIndicatorsPreview() {
    PolkadotTheme {
        ChainHealthIndicators(
            modifier = Modifier.padding(PolkadotTheme.spacings.medium),
            model = ChainHealthIndicatorsModel(
                persistentListOf(
                    previewItem("people", ChainGlyph.People, ChainHealthIndicator.Healthy),
                    previewItem("hub", ChainGlyph.AssetHub, ChainHealthIndicator.Outage(3, 5)),
                    previewItem("good", ChainGlyph.Bulletin, ChainHealthIndicator.ConnectionSpeed(Speed.Good)),
                    previewItem("fair", ChainGlyph.People, ChainHealthIndicator.ConnectionSpeed(Speed.Fair)),
                    previewItem("low", ChainGlyph.AssetHub, ChainHealthIndicator.ConnectionSpeed(Speed.Low)),
                    previewItem("connecting", ChainGlyph.AssetHub, ChainHealthIndicator.Connecting),
                    previewItem("dead", ChainGlyph.Bulletin, ChainHealthIndicator.Disconnected),
                ),
            ),
        )
    }
}

private fun previewItem(id: String, glyph: ChainGlyph, indicator: ChainHealthIndicator) = ChainHealthItemModel(
    chainId = id,
    chainName = id,
    glyph = glyph,
    indicator = indicator,
    expectedBlockTime = PREVIEW_BLOCK_TIME,
)
