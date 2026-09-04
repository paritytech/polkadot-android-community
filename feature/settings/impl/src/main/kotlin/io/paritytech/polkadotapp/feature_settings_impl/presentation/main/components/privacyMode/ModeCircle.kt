package io.paritytech.polkadotapp.feature_settings_impl.presentation.main.components.privacyMode

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import io.paritytech.polkadotapp.design.components.icon.NovaIcon
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.theme.PolkadotTheme

/**
 * One mode as it sits on the track. Selecting a mode grows its circle and lights it; the mode losing the
 * selection shrinks back in the same motion, so a tap reads as the selection passing between two circles
 * rather than one circle travelling along the track.
 *
 * The box is wider than the circle so the glow has room to spread without being clipped by the layout.
 */
@Composable
internal fun ModeCircle(
    modifier: Modifier,
    appearance: ModeAppearance,
    isSelected: Boolean,
    interactionSource: MutableInteractionSource? = null
) {
    val selection by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = SELECTION_ANIMATION,
        label = "circleSelection"
    )

    val diameter = lerp(CIRCLE_SIZE, SELECTED_CIRCLE_SIZE, selection)
    val glowColor = appearance.accentColor
    val shadowColor = PolkadotTheme.colors.avatar.bg.onyx

    Box(modifier = modifier.size(CIRCLE_BOX_SIZE), contentAlignment = Alignment.Center) {
        Spacer(
            modifier = Modifier
                .size(diameter)
                .drawWithCache {
                    val radius = size.minDimension / 2f
                    val centreX = size.width / 2f
                    val centreY = size.height / 2f

                    val shadowPaint = Paint().apply {
                        isAntiAlias = true
                        color = shadowColor.copy(alpha = SHADOW_ALPHA).toArgb()
                        maskFilter = BlurMaskFilter(SHADOW_BLUR.toPx(), BlurMaskFilter.Blur.NORMAL)
                    }
                    val glowPaint = Paint().apply {
                        isAntiAlias = true
                        color = glowColor.copy(alpha = selection * GLOW_ALPHA).toArgb()
                        maskFilter = BlurMaskFilter(GLOW_BLUR.toPx(), BlurMaskFilter.Blur.NORMAL)
                    }
                    val shadowOffset = SHADOW_OFFSET.toPx()

                    onDrawBehind {
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawCircle(centreX, centreY + shadowOffset, radius, shadowPaint)

                            if (selection > 0f) {
                                canvas.nativeCanvas.drawCircle(centreX, centreY, radius, glowPaint)
                            }
                        }
                    }
                }
        )

        PolkadotSurface(
            modifier = Modifier.size(diameter),
            shape = CircleShape,
            brush = appearance.circleBrush(selection),
            border = BorderStroke(CIRCLE_BORDER, appearance.circleBorderBrush())
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
                NovaIcon(
                    modifier = Modifier.size(diameter * MODE_ICON_SIZE_FRACTION),
                    imageVector = appearance.icon,
                    tint = appearance.iconColor
                )
            }
        }
    }
}

internal val CIRCLE_SIZE = 28.dp
internal val SELECTED_CIRCLE_SIZE = 40.dp

private val CIRCLE_BORDER = 1.dp

private val GLOW_BLUR = 12.dp

/** The glow is a halo, not a second light source: at full opacity the accent bleeds over the whole track. */
internal const val GLOW_ALPHA = 0.45f

private val SHADOW_BLUR = 4.dp
private val SHADOW_OFFSET = 4.dp
private const val SHADOW_ALPHA = 0.4f

/** Selected circle plus the glow spreading either side of it. */
internal val CIRCLE_BOX_SIZE = SELECTED_CIRCLE_SIZE + GLOW_BLUR * 2

/** Share of the circle the glyph takes up; the rest is the inset around it. */
private const val MODE_ICON_SIZE_FRACTION = 0.6f

internal val SELECTION_ANIMATION = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)
