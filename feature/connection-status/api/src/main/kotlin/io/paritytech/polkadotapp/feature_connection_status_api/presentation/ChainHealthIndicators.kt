package io.paritytech.polkadotapp.feature_connection_status_api.presentation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Color
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
import io.paritytech.polkadotapp.design.components.progress.NovaCircularProgressIndicator
import io.paritytech.polkadotapp.design.components.spacer.HorizontalSpacer
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainConnectionPresentation
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainGlyph
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator.Tone
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicatorsModel
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthItemModel
import kotlinx.collections.immutable.persistentListOf
import kotlin.math.PI

private val ICON_SIZE = 20.dp
private val GLYPH_SIZE = 10.dp
private val RING_STROKE = 2.dp
private const val RING_DOTS = 8
private const val PULSE_MIN_ALPHA = 0.3f
private const val PULSE_DURATION_MS = 900

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
    // Clickable icons otherwise inflate the row to the 48dp minimum touch target; the indicators are
    // deliberately tighter than that.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            model.chains.forEachIndexed { index, item ->
                if (index > 0) HorizontalSpacer { tiny }
                ChainHealthIcon(item = item)
            }
        }
    }
}

@Composable
private fun ChainHealthIcon(item: ChainHealthItemModel) {
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
            is ChainHealthIndicator.Degraded -> DegradedRing(item, indicator)
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
        contentColor = PolkadotTheme.colors.fg.primaryInverted,
        contentAlignment = Alignment.Center,
    ) {
        Glyph(item = item, tint = PolkadotTheme.colors.fg.primaryInverted)
    }
}

@Composable
private fun DegradedRing(item: ChainHealthItemModel, indicator: ChainHealthIndicator.Degraded) {
    NovaCircularProgressIndicator(
        modifier = Modifier.fillMaxSize(),
        progress = { indicator.fraction },
        color = indicator.tone.color(),
        trackColor = PolkadotTheme.colors.fg.tertiary,
        strokeWidth = RING_STROKE,
        strokeCap = StrokeCap.Round,
    )
    val glyphTint = when (indicator.tone) {
        Tone.Neutral -> PolkadotTheme.colors.fg.primary
        Tone.Warning, Tone.Error -> PolkadotTheme.colors.fg.secondary
    }
    Glyph(item = item, tint = glyphTint)
}

@Composable
private fun ConnectingRing(item: ChainHealthItemModel) {
    PolkadotSurface(
        modifier = Modifier.fillMaxSize(),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(RING_STROKE, PolkadotTheme.colors.fg.tertiary),
    ) {}
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
    val color = PolkadotTheme.colors.fg.disabled
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val stroke = RING_STROKE.toPx()
                val radius = (size.minDimension - stroke) / 2
                // The dash period must divide the circumference exactly or the last dot overlaps the first.
                val period = (2 * PI * radius / RING_DOTS).toFloat()
                drawCircle(
                    color = color,
                    radius = radius,
                    style = Stroke(
                        width = stroke,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke, period - stroke)),
                    ),
                )
            },
    )
    Glyph(item = item, tint = color)
}

@Composable
private fun Glyph(item: ChainHealthItemModel, tint: Color) {
    NovaIcon(
        // The healthy disc is a surface that propagates its 20dp minimum to its content; size() would yield to it.
        modifier = Modifier.requiredSize(GLYPH_SIZE),
        imageVector = item.glyph.imageVector(),
        tint = tint,
        contentDescription = item.chainName,
    )
}

@Composable
private fun Tone.color(): Color = when (this) {
    Tone.Neutral -> PolkadotTheme.colors.fg.primary
    Tone.Warning -> PolkadotTheme.colors.fg.warning
    Tone.Error -> PolkadotTheme.colors.fg.error
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
                    previewItem("hub", ChainGlyph.AssetHub, ChainHealthIndicator.Degraded(Tone.Warning, 0.5f)),
                    previewItem("bulletin", ChainGlyph.Bulletin, ChainHealthIndicator.Degraded(Tone.Error, 0.25f)),
                    previewItem("connecting", ChainGlyph.People, ChainHealthIndicator.Connecting),
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
