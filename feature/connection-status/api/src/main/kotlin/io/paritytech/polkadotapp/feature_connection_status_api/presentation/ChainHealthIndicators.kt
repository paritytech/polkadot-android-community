package io.paritytech.polkadotapp.feature_connection_status_api.presentation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainConnectionPresentation
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainGlyph
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator.Speed
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicatorsModel
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthItemModel
import kotlinx.collections.immutable.persistentListOf

private val ICON_SIZE = 20.dp
private val GLYPH_SIZE = 10.dp
private val RING_STROKE = 2.dp
private const val PULSE_MIN_ALPHA = 0.3f
private const val PULSE_DURATION_MS = 900
private const val FULL_TURN = 360f
private const val TOP_ANGLE = -90f

/**
 * One tappable indicator per monitored chain, meant for a title bar's trailing slot. The inner glyph
 * names the chain; the disc or ring around it draws [ChainHealthIndicator]. Tapping opens that
 * chain's [ChainHealthDetailsPopover].
 */
@Composable
fun ChainHealthIndicators(
    modifier: Modifier = Modifier,
    model: ChainHealthIndicatorsModel,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(PolkadotTheme.spacings.tiny),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            model.chains.forEach { item -> Indicator(item = item) }
        }
    }
}

@Composable
private fun Indicator(item: ChainHealthItemModel) {
    var showDetails by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(ICON_SIZE)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { showDetails = true },
        contentAlignment = Alignment.Center,
    ) {
        when (val indicator = item.indicator) {
            ChainHealthIndicator.Healthy -> HealthyDisc(item)
            is ChainHealthIndicator.Outage -> OutageArc(item, indicator)
            is ChainHealthIndicator.SlowConnection -> SlowRing(item, indicator)
            ChainHealthIndicator.Connecting -> ConnectingRing(item)
            ChainHealthIndicator.Disconnected -> DisconnectedRing(item)
        }

        ChainHealthDetailsPopover(
            expanded = showDetails,
            item = item,
            onDismiss = { showDetails = false },
        )
    }
}

@Composable
private fun HealthyDisc(item: ChainHealthItemModel) {
    PolkadotSurface(
        modifier = Modifier.fillMaxSize(),
        shape = CircleShape,
        color = PolkadotTheme.colors.fg.primary,
        contentAlignment = Alignment.Center,
    ) {
        Glyph(item = item, tint = PolkadotTheme.colors.fg.primaryInverted)
    }
}

@Composable
private fun OutageArc(item: ChainHealthItemModel, indicator: ChainHealthIndicator.Outage) {
    val arcColor = PolkadotTheme.colors.fg.error
    val trackColor = PolkadotTheme.colors.fg.tertiary
    val sweep = -FULL_TURN * indicator.recentBlocks / indicator.expectedBlocks
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val stroke = RING_STROKE.toPx()
                val inset = Offset(stroke / 2, stroke / 2)
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawCircle(color = trackColor, radius = (size.minDimension - stroke) / 2, style = Stroke(stroke))
                drawArc(
                    color = arcColor,
                    startAngle = TOP_ANGLE,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = inset,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            },
    )
    Glyph(item = item, tint = PolkadotTheme.colors.fg.secondary)
}

@Composable
private fun SlowRing(item: ChainHealthItemModel, indicator: ChainHealthIndicator.SlowConnection) {
    val ringColor = when (indicator.speed) {
        Speed.Slow -> PolkadotTheme.colors.fg.warning
        Speed.Unusable -> PolkadotTheme.colors.fg.error
    }
    Ring(color = ringColor)
    Glyph(item = item, tint = PolkadotTheme.colors.fg.secondary)
}

@Composable
private fun ConnectingRing(item: ChainHealthItemModel) {
    Ring(color = PolkadotTheme.colors.fg.tertiary)
    val pulse = rememberInfiniteTransition(label = "ChainConnectingPulse")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = PULSE_MIN_ALPHA,
        animationSpec = infiniteRepeatable(tween(PULSE_DURATION_MS), RepeatMode.Reverse),
        label = "ChainConnectingGlyphAlpha",
    )
    Box(modifier = Modifier.graphicsLayer { this.alpha = alpha }) {
        Glyph(item = item, tint = PolkadotTheme.colors.fg.secondary)
    }
}

@Composable
private fun DisconnectedRing(item: ChainHealthItemModel) {
    Ring(color = PolkadotTheme.colors.fg.disabled)
    Glyph(item = item, tint = PolkadotTheme.colors.fg.disabled)
}

@Composable
private fun Ring(color: Color) {
    PolkadotSurface(
        modifier = Modifier.fillMaxSize(),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(RING_STROKE, color),
    ) {}
}

@Composable
private fun Glyph(item: ChainHealthItemModel, tint: Color) {
    NovaIcon(
        modifier = Modifier.requiredSize(GLYPH_SIZE),
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
                    previewItem("bulletin", ChainGlyph.Bulletin, ChainHealthIndicator.SlowConnection(Speed.Slow)),
                    previewItem("unusable", ChainGlyph.People, ChainHealthIndicator.SlowConnection(Speed.Unusable)),
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
    connection = ChainConnectionPresentation.Connected,
    indicator = indicator,
    readings = persistentListOf(),
)
