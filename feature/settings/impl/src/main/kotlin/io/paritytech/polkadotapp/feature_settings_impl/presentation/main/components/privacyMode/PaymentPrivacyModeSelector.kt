package io.paritytech.polkadotapp.feature_settings_impl.presentation.main.components.privacyMode

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.paritytech.polkadotapp.design.components.icon.NovaIcon
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.ShieldLock
import io.paritytech.polkadotapp.design.components.spacer.HorizontalSpacer
import io.paritytech.polkadotapp.design.components.spacer.VerticalSpacer
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.RecyclingStrategyType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import io.paritytech.polkadotapp.common.R as RCommon

/**
 * The primary operating-mode control of the payment system: how much of what the user receives is held back
 * to gain privacy before it can be spent again.
 *
 * The selection is marked by a pair of arrows that can be dragged along the track as well as tapped into
 * place. The track carries intermediate units between the three presets: the underlying model is continuous
 * in two parameters, so the positions between presets are the ones a later release opens up.
 *
 * Quota is deliberately absent from the copy — it is managed automatically and the user cannot act on it.
 */
@Composable
fun PaymentPrivacyModeSelector(
    modifier: Modifier = Modifier,
    shape: Shape,
    selectedMode: RecyclingStrategyType,
    onModeSelected: (RecyclingStrategyType) -> Unit
) {
    PolkadotSurface(
        modifier = modifier,
        shape = shape,
        color = PolkadotTheme.colors.bg.surface.container
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PolkadotTheme.spacings.mediumIncreased)
        ) {
            Header()

            VerticalSpacer { small }

            ModeSelector(
                selectedMode = selectedMode,
                onModeSelected = onModeSelected
            )
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.Top) {
        NovaIcon(
            modifier = Modifier.size(HEADER_ICON_SIZE),
            imageVector = NovaIcons.ShieldLock,
            tint = PolkadotTheme.colors.fg.secondary
        )

        HorizontalSpacer { small }

        Column(modifier = Modifier.weight(1f)) {
            NovaText(
                text = stringResource(RCommon.string.payment_privacy_mode_title),
                style = PolkadotTheme.typography.title.small,
                color = PolkadotTheme.colors.fg.primary
            )

            VerticalSpacer { extraTiny }

            NovaText(
                text = stringResource(RCommon.string.payment_privacy_mode_subtitle),
                style = PolkadotTheme.typography.body.small,
                color = PolkadotTheme.colors.fg.secondary
            )
        }
    }
}

@Composable
private fun ModeSelector(
    selectedMode: RecyclingStrategyType,
    onModeSelected: (RecyclingStrategyType) -> Unit
) {
    val modes = RecyclingStrategyType.entries
    val appearances = modes.map { it.appearance() }.toImmutableList()
    val selectedIndex = modes.indexOf(selectedMode)

    val scope = rememberCoroutineScope()
    // Position is held as a fractional mode index rather than pixels, so it is already correct on the first
    // frame — before the row has been measured and a pixel anchor could be known.
    val position = remember { Animatable(selectedIndex.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    var trackWidth by remember { mutableIntStateOf(0) }

    val interactionSources = remember { List(modes.size) { MutableInteractionSource() }.toImmutableList() }

    LaunchedEffect(selectedIndex) {
        if (!isDragging && position.targetValue != selectedIndex.toFloat()) {
            position.animateTo(selectedIndex.toFloat(), SELECTION_ANIMATION)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { trackWidth = it.width }
    ) {
        // The visuals carry no semantics of their own; the touch targets below describe each mode in one
        // node, so a screen reader reads a mode once rather than as three loose fragments.
        Column(modifier = Modifier.clearAndSetSemantics { }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(INDICATOR_BAND_HEIGHT)
            ) {
                ConnectorTrack(gapCount = modes.size - 1)

                ModeCircles(appearances = appearances, interactionSources = interactionSources)

                SelectionArrows(
                    appearances = appearances,
                    position = { position.value },
                    trackWidth = { trackWidth },
                    cellCount = modes.size
                )
            }

            VerticalSpacer { small }

            ModeLabels(appearances)

            VerticalSpacer { extraTiny }

            ModeDescriptions(appearances)
        }

        // The drag lives on the parent of the touch targets: a tap never crosses the slop, so it reaches the
        // cell below, and once a drag starts the cell's own click is cancelled.
        Row(
            modifier = Modifier
                .matchParentSize()
                .selectableGroup()
                .pointerInput(modes.size) {
                    detectHorizontalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragCancel = { isDragging = false },
                        onDragEnd = {
                            isDragging = false

                            val nearest = position.value.roundToInt().coerceIn(modes.indices)
                            scope.launch { position.animateTo(nearest.toFloat(), SELECTION_ANIMATION) }
                            onModeSelected(modes[nearest])
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()

                            val cellWidth = size.width / modes.size.toFloat()
                            val dragged = change.position.x / cellWidth - CELL_CENTRE_FRACTION
                            scope.launch {
                                position.snapTo(dragged.coerceIn(0f, modes.lastIndex.toFloat()))
                            }
                        }
                    )
                }
        ) {
            modes.forEachIndexed { index, mode ->
                ModeTouchTarget(
                    appearance = appearances[index],
                    isSelected = mode == selectedMode,
                    interactionSource = interactionSources[index],
                    onClick = { onModeSelected(mode) }
                )
            }
        }
    }
}

@Composable
private fun ModeLabels(appearances: ImmutableList<ModeAppearance>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        appearances.forEach { appearance ->
            NovaText(
                modifier = Modifier.weight(1f),
                text = appearance.label,
                style = PolkadotTheme.typography.title.tiny,
                color = appearance.labelColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ModeDescriptions(appearances: ImmutableList<ModeAppearance>) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        appearances.forEach { appearance ->
            NovaText(
                modifier = Modifier.weight(1f),
                text = appearance.description,
                style = PolkadotTheme.typography.label.small,
                color = PolkadotTheme.colors.fg.secondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RowScope.ModeTouchTarget(
    appearance: ModeAppearance,
    isSelected: Boolean,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit
) {
    val itemDescription = appearance.accessibilityDescription
    val itemState = stringResource(
        if (isSelected) {
            RCommon.string.payment_privacy_mode_state_selected
        } else {
            RCommon.string.payment_privacy_mode_state_not_selected
        }
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            // A ripple across label and description would read as a card press, so the indication is handed
            // to the circle instead — the thing the user is actually choosing.
            .selectable(
                selected = isSelected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick
            )
            .semantics {
                contentDescription = itemDescription
                stateDescription = itemState
            }
    )
}

private val HEADER_ICON_SIZE = 24.dp

private val SELECTION_ANIMATION = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)

@Preview
@Composable
private fun PaymentPrivacyModeSelectorPreview() {
    PolkadotTheme {
        Column(
            modifier = Modifier.padding(PolkadotTheme.spacings.mediumIncreased)
        ) {
            RecyclingStrategyType.entries.forEach { mode ->
                PaymentPrivacyModeSelector(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(PolkadotTheme.radii.large),
                    selectedMode = mode,
                    onModeSelected = {}
                )

                VerticalSpacer { mediumIncreased }
            }
        }
    }
}
