package io.paritytech.polkadotapp.feature_settings_impl.presentation.main.components.privacyMode

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import io.paritytech.polkadotapp.design.components.icon.NovaIcon
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import androidx.compose.ui.util.lerp as lerpFloat

// One mode as it sits on the track. Selecting a mode grows its circle and lights it; the mode losing the
// selection shrinks back in the same motion, so a tap reads as the selection passing between two circles
// rather than one circle travelling along the track.
// A dragged circle is a single circle that adopts each mode as it passes the midpoint towards it, so
// [appearance] changes under it mid-gesture and is cross-faded rather than swapped — see [fadeMillis].
// The glow and the ring are what say a mode has been settled on rather than merely passed over, so they are
// [isSettled]'s to decide and not the selected size's: a circle under a finger is grown but unlit and unringed.
// The box reserves row height for the ring and its shadow and sizes the touch row; the glow may spill past it.
@Composable
internal fun ModeCircle(
    modifier: Modifier,
    appearance: ModeAppearance,
    isSelected: Boolean,
    isSettled: Boolean,
    fadeMillis: () -> Int,
    interactionSource: MutableInteractionSource? = null
) {
    val selection by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = SELECTION_ANIMATION,
        label = "circleSelection"
    )
    val glow by animateFloatAsState(
        targetValue = if (isSettled) 1f else 0f,
        animationSpec = SELECTION_ANIMATION,
        label = "circleGlow"
    )

    // The mode the circle is coming from, held until the fade away from it completes. A crossing that
    // interrupts an unfinished fade restarts from this same mode: two crossings inside one fade would mean
    // the finger covered half a mode in under one fade, which is faster than the track allows.
    var fadeFrom by remember { mutableStateOf(appearance) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(appearance) {
        if (fadeFrom == appearance) return@LaunchedEffect

        fade.snapTo(0f)
        // Linear on purpose: two glyphs dissolving into each other with an eased curve lose ink in the
        // middle of the exchange, which reads as a blink.
        fade.animateTo(1f, tween(durationMillis = fadeMillis(), easing = LinearEasing))
        fadeFrom = appearance
    }

    // Composition sees the new mode one frame before the effect above can start the animation, so until it
    // is running the circle stays on the outgoing mode instead of flashing the incoming one.
    val progress = when {
        fadeFrom == appearance -> 1f
        fade.isRunning -> fade.value
        else -> 0f
    }
    val blended = appearance.blendedFrom(fadeFrom, progress)

    val diameter = lerp(CIRCLE_SIZE, SELECTED_CIRCLE_SIZE, selection)
    val glowColor = blended.colors.glow
    val ringColors = blended.colors.selected
    val shadowColor = PolkadotTheme.colors.shadow.medium
    val shadowAlpha = lerpFloat(UNSELECTED_SHADOW_ALPHA, SELECTED_SHADOW_ALPHA, selection)

    // Held across frames and reconfigured in place: the circle's size animates, which rebuilds the draw
    // cache on every frame of a selection change, so anything created there is created per frame.
    val density = LocalDensity.current
    val shadowBlur = remember(density) {
        with(density) { BlurMaskFilter(SHADOW_BLUR.toPx(), BlurMaskFilter.Blur.NORMAL) }
    }
    val glowBlur = remember(density) {
        with(density) { BlurMaskFilter(GLOW_BLUR.toPx(), BlurMaskFilter.Blur.NORMAL) }
    }
    val shadowPaint = remember(shadowBlur) {
        Paint().apply {
            isAntiAlias = true
            maskFilter = shadowBlur
        }
    }
    val glowPaint = remember(glowBlur) {
        Paint().apply {
            isAntiAlias = true
            maskFilter = glowBlur
        }
    }
    val ringShadowPaint = remember(shadowBlur, density) {
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = with(density) { RING_STROKE.toPx() }
            maskFilter = shadowBlur
        }
    }
    val ringStroke = remember(density) { with(density) { Stroke(width = RING_STROKE.toPx()) } }
    val ringBrush = remember(ringColors, density) {
        with(density) {
            Brush.verticalGradient(
                colors = listOf(ringColors.rimTop, ringColors.rimBottom),
                startY = RING_TOP_INSET.toPx(),
                endY = (RING_TOP_INSET + RING_OUTER_SIZE - RING_STROKE).toPx()
            )
        }
    }

    Box(
        modifier = Modifier
            .size(CIRCLE_BOX_SIZE)
            .then(modifier)
            // The ring sits above the disc in the design, so its shadow falls onto the disc's lower edge.
            .drawWithCache {
                val centreX = size.width / 2f
                val centreY = size.height / 2f
                val half = (RING_OUTER_SIZE - RING_STROKE).toPx() / 2f
                val corner = (RING_CORNER_RADIUS - RING_STROKE / 2).toPx()
                val shadowOffset = SHADOW_OFFSET.toPx()
                val ringTopLeft = Offset(centreX - half, centreY - half)
                val ringSize = Size(half * 2f, half * 2f)

                ringShadowPaint.color = shadowColor.copy(alpha = glow * SELECTED_SHADOW_ALPHA).toArgb()

                onDrawWithContent {
                    drawContent()

                    if (glow > 0f) {
                        drawIntoCanvas { canvas ->
                            val native = canvas.nativeCanvas

                            native.drawRoundRect(
                                centreX - half,
                                centreY - half + shadowOffset,
                                centreX + half,
                                centreY + half + shadowOffset,
                                corner,
                                corner,
                                ringShadowPaint
                            )
                        }

                        drawRoundRect(
                            brush = ringBrush,
                            topLeft = ringTopLeft,
                            size = ringSize,
                            cornerRadius = CornerRadius(corner),
                            alpha = glow,
                            style = ringStroke
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Spacer(
            modifier = Modifier
                .size(diameter)
                .drawWithCache {
                    val radius = size.minDimension / 2f
                    val centreX = size.width / 2f
                    val centreY = size.height / 2f
                    val shadowOffset = SHADOW_OFFSET.toPx()
                    val glowHalf = blended.glowGeometry.size.toPx() / 2f
                    val glowCorner = blended.glowGeometry.cornerRadius.toPx()

                    shadowPaint.color = shadowColor.copy(alpha = shadowAlpha).toArgb()
                    glowPaint.color = glowColor.copy(alpha = glow * GLOW_ALPHA).toArgb()

                    onDrawBehind {
                        drawIntoCanvas { canvas ->
                            val native = canvas.nativeCanvas

                            if (glow > 0f) {
                                native.drawRoundRect(
                                    centreX - glowHalf,
                                    centreY - glowHalf,
                                    centreX + glowHalf,
                                    centreY + glowHalf,
                                    glowCorner,
                                    glowCorner,
                                    glowPaint
                                )
                            }

                            native.drawCircle(centreX, centreY + shadowOffset, radius, shadowPaint)
                        }
                    }
                }
        )

        PolkadotSurface(
            modifier = Modifier.size(diameter),
            shape = CircleShape,
            brush = blended.circleBrush(selection),
            border = BorderStroke(CIRCLE_BORDER, blended.circleBorderBrush(selection))
        ) {
            val ripple = if (interactionSource != null) {
                Modifier.indication(interactionSource, LocalIndication.current)
            } else {
                Modifier
            }

            // PolkadotSurface propagates its minimum constraints, so a size on the icon itself would be
            // clamped straight back up to the circle. This Box absorbs the minimum and lets the inset stand.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(ripple),
                contentAlignment = Alignment.Center
            ) {
                val iconSize = diameter * MODE_ICON_SIZE_FRACTION

                if (progress < 1f) {
                    NovaIcon(
                        modifier = Modifier.size(iconSize).alpha(1f - progress),
                        imageVector = fadeFrom.icon,
                        tint = PrivacyModeColors.Glyph
                    )
                }

                NovaIcon(
                    modifier = Modifier.size(iconSize).alpha(progress),
                    imageVector = appearance.icon,
                    tint = PrivacyModeColors.Glyph
                )
            }
        }
    }
}

private val CIRCLE_SIZE = 28.dp
private val SELECTED_CIRCLE_SIZE = 50.dp

private val CIRCLE_BORDER = 1.dp

private val RING_OUTER_SIZE = 62.dp
private val RING_CORNER_RADIUS = 24.dp
private val RING_STROKE = 2.dp

// Figma blurs the glow with a Gaussian sigma of ~12dp; BlurMaskFilter's radius is ~1.7x the sigma it yields.
private val GLOW_BLUR = 20.dp
private const val GLOW_ALPHA = 0.5f

private val SHADOW_BLUR = 4.dp
private val SHADOW_OFFSET = 4.dp

// The palette's shadow colour is black at 48%; the design asks for these depths, with nothing darker in the
// set to reach for, so the token supplies the colour and the alphas supply the depth the design drew.
private const val SELECTED_SHADOW_ALPHA = 0.7f
private const val UNSELECTED_SHADOW_ALPHA = 0.5f

internal val CIRCLE_BOX_SIZE = RING_OUTER_SIZE + (SHADOW_OFFSET + SHADOW_BLUR) * 2

// The ring's gradient spans the ring, not the box it sits in.
private val RING_TOP_INSET = (CIRCLE_BOX_SIZE - RING_OUTER_SIZE + RING_STROKE) / 2

// Share of the circle the glyph takes up; the rest is the inset around it.
private const val MODE_ICON_SIZE_FRACTION = 0.64f

internal val SELECTION_ANIMATION = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)
