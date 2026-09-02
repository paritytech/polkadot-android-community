package io.paritytech.polkadotapp.feature_settings_impl.presentation.main.components.privacyMode

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.paritytech.polkadotapp.design.components.icon.NovaIcon
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.ArrowDropdown
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.roundToInt

@Composable
internal fun ModeCircles(
    appearances: ImmutableList<ModeAppearance>,
    interactionSources: ImmutableList<MutableInteractionSource>
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        appearances.forEachIndexed { index, appearance ->
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                PolkadotSurface(
                    modifier = Modifier.size(CIRCLE_SIZE),
                    shape = CircleShape,
                    color = appearance.accentColor
                ) {
                    // PolkadotSurface propagates its minimum constraints, so a size on the icon itself would
                    // be clamped straight back up to the circle. This Box absorbs the minimum and lets the
                    // inset stand.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .indication(interactionSources[index], LocalIndication.current),
                        contentAlignment = Alignment.Center
                    ) {
                        NovaIcon(
                            modifier = Modifier.size(CIRCLE_SIZE * MODE_ICON_SIZE_FRACTION),
                            imageVector = appearance.icon,
                            tint = appearance.iconColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * The track runs centre-to-centre between the outer circles: a half cell of padding on each side, then one
 * full cell per gap. Neighbouring cells are the same width, so the segments meet into one continuous line.
 */
@Composable
internal fun ConnectorTrack(gapCount: Int) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(CELL_CENTRE_FRACTION))

        repeat(gapCount) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.width(CIRCLE_SIZE / 2 + CONNECTOR_GAP))

                repeat(INTERMEDIATE_UNIT_COUNT + 1) { segment ->
                    if (segment > 0) IntermediateUnit()

                    ConnectorLine()
                }

                Box(modifier = Modifier.width(CIRCLE_SIZE / 2 + CONNECTOR_GAP))
            }
        }

        Box(modifier = Modifier.weight(CELL_CENTRE_FRACTION))
    }
}

@Composable
private fun RowScope.ConnectorLine() {
    PolkadotSurface(
        modifier = Modifier
            .weight(1f)
            .height(CONNECTOR_THICKNESS),
        color = PolkadotTheme.colors.stroke.secondary
    ) {}
}

/** A position between two presets that the strategy model already supports but the UI does not yet offer. */
@Composable
private fun IntermediateUnit() {
    PolkadotSurface(
        modifier = Modifier
            .width(CONNECTOR_THICKNESS)
            .height(INTERMEDIATE_UNIT_HEIGHT),
        color = PolkadotTheme.colors.stroke.secondary
    ) {}
}

/**
 * The pair of arrows that brackets the current position. [position] is a fractional mode index, so the
 * arrows track the finger continuously rather than jumping between cells.
 */
@Composable
internal fun BoxScope.SelectionArrows(
    appearances: ImmutableList<ModeAppearance>,
    position: () -> Float,
    trackWidth: () -> Int,
    cellCount: Int
) {
    val accentColors = appearances.map { it.accentColor }
    // The position changes every frame while dragging, but the arrow only ever takes the colour of the mode
    // it is closest to, which changes a handful of times across the whole gesture.
    val tint by remember(accentColors) {
        derivedStateOf { accentColors[position().roundToInt().coerceIn(accentColors.indices)] }
    }

    val offset: Density.() -> IntOffset = {
        val centre = (position() + CELL_CENTRE_FRACTION) * trackWidth() / cellCount
        IntOffset(x = (centre - ARROW_SIZE.toPx() / 2).roundToInt(), y = 0)
    }

    NovaIcon(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(offset)
            .size(ARROW_SIZE),
        imageVector = NovaIcons.ArrowDropdown,
        tint = tint
    )

    NovaIcon(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .offset(offset)
            .size(ARROW_SIZE)
            .rotate(HALF_TURN_DEGREES),
        imageVector = NovaIcons.ArrowDropdown,
        tint = tint
    )
}

private val CIRCLE_SIZE = 52.dp

private val ARROW_SIZE = 24.dp

/** Circle plus an arrow above and below it. */
internal val INDICATOR_BAND_HEIGHT = CIRCLE_SIZE + ARROW_SIZE * 2

/** Share of the circle the glyph takes up; the rest is the inset around it. */
private const val MODE_ICON_SIZE_FRACTION = 0.7f

/** Where a mode sits inside its own cell — the middle. */
internal const val CELL_CENTRE_FRACTION = 0.5f

private const val HALF_TURN_DEGREES = 180f

private val CONNECTOR_THICKNESS = 1.dp

private val CONNECTOR_GAP = 6.dp

private const val INTERMEDIATE_UNIT_COUNT = 3

private val INTERMEDIATE_UNIT_HEIGHT = 8.dp
