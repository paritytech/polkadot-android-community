package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.components.id

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_wallet_impl.R
import io.paritytech.polkadotapp.feature_wallet_impl.domain.model.PocketRank
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.animation.LocalCardTilt
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.animation.TiltState
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.components.CardSizes
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.components.PocketCardColors
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.models.PocketCardUiModel
import kotlin.math.abs
import io.paritytech.polkadotapp.common.R as RCommon

private val MemberBorderBrush = Brush.verticalGradient(
    listOf(PocketCardColors.Primary, PocketCardColors.MemberBorderBottom)
)

// Card base, shown under the (mostly opaque) bg image
private val CardBaseColor = Color(0xFF1A1A22)

private const val WM_WIDTH_RATIO = 420f / 372f
private const val WM_REST_Y_RATIO = 0.659f

// Elements layer (our live Compose content) moves very slightly for depth, per GyroShineTest 2.
private const val ELEMENTS_OFFSET_X = 16f
private const val ELEMENTS_OFFSET_Y = 10f
private const val ELEMENTS_SCALE = 1.03f

// Wordmark moves less than the rest of the elements (its own reduced parallax, no scale).
private const val WM_OFFSET_X = 8f
private const val WM_OFFSET_Y = 5f

// Holographic tilt animation. Tilt drives: a subtle whole-card
// 3-D pivot, a slow background parallax, a faster shine-image parallax, and a programmatic specular band.
// Offsets are in px (graphicsLayer translation); the shine moves much further than the bg for depth.
private const val CARD_TILT_DEGREES = 5f
private const val CARD_CAMERA_DISTANCE_DP = 12f

private const val BG_OFFSET_X = 35f
private const val BG_OFFSET_Y = 24f
private const val BG_SCALE = 1.08f

private const val SHINE_OFFSET_X = 180f
private const val SHINE_OFFSET_Y = 120f
private const val SHINE_SCALE = 1.35f

// Shine image opacity ramps with how far the device is tilted from rest, clamped to a visible band.
private const val SHINE_ALPHA_BASE = 0.38f
private const val SHINE_ALPHA_POWER = 0.32f
private const val SHINE_ALPHA_MIN = 0.30f
private const val SHINE_ALPHA_MAX = 0.90f

// Programmatic specular band: a soft white diagonal sweep whose centre tracks the tilt.
private const val SHINE_BAND_CENTER_X_FACTOR = 0.45f
private const val SHINE_BAND_CENTER_Y_FACTOR = 0.35f
private const val SHINE_BAND_WIDTH_FACTOR = 0.42f
private const val SHINE_BAND_EDGE_ALPHA = 0.08f
private const val SHINE_BAND_CORE_ALPHA = 0.38f

// Tilt-driven shine opacity, computed in the draw phase from the current tilt. neutral = (x, y - 1).
private fun TiltState.shineAlpha(): Float =
    (SHINE_ALPHA_BASE + (abs(x) + abs(y - 1f)) * SHINE_ALPHA_POWER).coerceIn(SHINE_ALPHA_MIN, SHINE_ALPHA_MAX)

@Composable
internal fun MemberIdCard(
    modifier: Modifier,
    card: PocketCardUiModel.IdCard,
    onSelected: ((PocketCardUiModel.IdCard) -> Unit)?,
    onQrClick: () -> Unit
) {
    // Read the tilt State in the draw phase (inside each graphicsLayer / drawWithContent), not via `by` at
    // composition, so per-frame sensor updates don't recompose the card + its content. neutral = tilt.y - 1.
    val tiltState = LocalCardTilt.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(CardSizes.HEIGHT)
            .graphicsLayer {
                // Inverted whole-card 3-D pivot.
                val t = tiltState.value
                rotationX = -(t.y - 1f) * CARD_TILT_DEGREES
                rotationY = t.x * CARD_TILT_DEGREES
                cameraDistance = CARD_CAMERA_DISTANCE_DP * density
                transformOrigin = TransformOrigin.Center
            }
            .clip(PolkadotTheme.shapes.large)
            .clickable(
                enabled = onSelected != null,
                onClick = { onSelected?.invoke(card) }
            )
            .background(CardBaseColor)
            .border(
                BorderStroke(PolkadotTheme.borders.default, MemberBorderBrush),
                PolkadotTheme.shapes.large
            )
    ) {
        // Background layer: slow inverted parallax.
        Image(
            painter = painterResource(R.drawable.member_card_holo_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val t = tiltState.value
                    translationX = -t.x * BG_OFFSET_X
                    translationY = -(t.y - 1f) * BG_OFFSET_Y
                    scaleX = BG_SCALE
                    scaleY = BG_SCALE
                }
        )

        // Shine layer: faster inverted parallax + tilt-driven opacity. The image has transparent edges, so it
        // pans freely without exposing a seam.
        Image(
            painter = painterResource(R.drawable.member_card_holo_shine),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val t = tiltState.value
                    translationX = -t.x * SHINE_OFFSET_X
                    translationY = -(t.y - 1f) * SHINE_OFFSET_Y
                    scaleX = SHINE_SCALE
                    scaleY = SHINE_SCALE
                    alpha = t.shineAlpha()
                }
        )

        val cardWidth = maxWidth
        val cardHeight = maxHeight

        // Wordmark: its own reduced parallax (moves less than the rest of the elements).
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val t = tiltState.value
                    translationX = -t.x * WM_OFFSET_X
                    translationY = -(t.y - 1f) * WM_OFFSET_Y
                }
        ) {
            Wordmark(cardHeight, cardWidth)
        }

        // Elements layer: our live content (avatar / username / rank / QR), slight parallax for depth
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val t = tiltState.value
                    translationX = -t.x * ELEMENTS_OFFSET_X
                    translationY = -(t.y - 1f) * ELEMENTS_OFFSET_Y
                    scaleX = ELEMENTS_SCALE
                    scaleY = ELEMENTS_SCALE
                }
        ) {
            IdCardContent(
                username = card.username,
                avatarPainter = painterResource(R.drawable.member_card_avatar_placeholder),
                rankValue = stringResource(RCommon.string.identity_card_rank_member),
                primaryTextColor = PocketCardColors.PrimaryInverted,
                secondaryTextColor = PocketCardColors.SecondaryInverted,
                onQrClick = onQrClick
            )
        }

        // Programmatic specular band on top.
        Box(
            modifier = Modifier
                .matchParentSize()
                .gyroGradientShine(tiltState)
        )
    }
}

private fun Modifier.gyroGradientShine(
    tiltState: State<TiltState>
): Modifier = this
    .graphicsLayer { this.alpha = tiltState.value.shineAlpha() }
    .drawWithContent {
        drawContent()
        val t = tiltState.value
        drawGyroShine(tiltX = -t.x, tiltY = -(t.y - 1f), size = size)
    }

private fun DrawScope.drawGyroShine(
    tiltX: Float,
    tiltY: Float,
    size: Size
) {
    val centerX = size.width * (0.5f + tiltX * SHINE_BAND_CENTER_X_FACTOR)
    val centerY = size.height * (0.5f + tiltY * SHINE_BAND_CENTER_Y_FACTOR)
    val bandWidth = size.width * SHINE_BAND_WIDTH_FACTOR

    val brush = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            Color.White.copy(alpha = SHINE_BAND_EDGE_ALPHA),
            Color.White.copy(alpha = SHINE_BAND_CORE_ALPHA),
            Color.White.copy(alpha = SHINE_BAND_EDGE_ALPHA),
            Color.Transparent
        ),
        start = Offset(centerX - bandWidth, centerY - size.height),
        end = Offset(centerX + bandWidth, centerY + size.height)
    )

    clipRect {
        drawRect(brush = brush, size = size)
    }
}

@Composable
private fun Wordmark(
    height: Dp,
    width: Dp
) {
    val wmWidth = width * WM_WIDTH_RATIO
    val wmHeight = wmWidth * 0.214f
    Image(
        painter = painterResource(R.drawable.member_card_polkadot_wordmark),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier
            .requiredSize(wmWidth, wmHeight)
            .graphicsLayer { translationY = height.toPx() * WM_REST_Y_RATIO }
    )
}

@Preview
@Composable
private fun MemberIdCardPreview() {
    PolkadotTheme {
        IdCard(card = PocketCardUiModel.IdCard("username.99", "15oF4u...zaC1Ap", PocketRank.Member))
    }
}
