package io.paritytech.polkadotapp.feature_products_impl.presentation.compose

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.paritytech.polkadotapp.design.components.progress.NovaCircularProgressIndicator
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsLoadProgress

@Composable
fun DotNsLoadProgressCircle(modifier: Modifier = Modifier, progress: DotNsLoadProgress) {
    val target = when (progress) {
        DotNsLoadProgress.Idle, DotNsLoadProgress.Resolving -> RESOLVE_BAND_END

        is DotNsLoadProgress.Downloading ->
            progress.fraction?.let { RESOLVE_BAND_END + (DOWNLOAD_BAND_END - RESOLVE_BAND_END) * it } ?: RESOLVE_BAND_END

        DotNsLoadProgress.Unpacking, DotNsLoadProgress.Completed -> 1f
        is DotNsLoadProgress.Failed -> 0f
    }

    val animationSpec = if (progress is DotNsLoadProgress.Downloading) DOWNLOADING_SPEC else BAND_SPEC

    val animatedFraction by animateFloatAsState(
        targetValue = target,
        animationSpec = animationSpec,
        label = "dotNsLoadFraction",
    )

    // Spins even while determinate so a phase without byte counts still reads as working.
    val spin = rememberInfiniteTransition(label = "dotNsLoadSpin").animateFloat(
        initialValue = 0f,
        targetValue = FULL_TURN_DEGREES,
        animationSpec = infiniteRepeatable(
            animation = tween(SPIN_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dotNsLoadSpinAngle",
    )

    NovaCircularProgressIndicator(
        modifier = modifier
            .size(INDICATOR_SIZE)
            .graphicsLayer { rotationZ = spin.value },
        progress = { animatedFraction },
        color = PolkadotTheme.colors.fg.link,
        trackColor = PolkadotTheme.colors.fg.tertiary,
    )
}

private const val RESOLVE_BAND_END = 0.1f
private const val DOWNLOAD_BAND_END = 0.9f
private const val BAND_ANIM_MILLIS = 300
private const val SPIN_MILLIS = 1200
private const val FULL_TURN_DEGREES = 360f
private val INDICATOR_SIZE = 48.dp
private val DOWNLOADING_SPEC: AnimationSpec<Float> = spring(stiffness = Spring.StiffnessLow)
private val BAND_SPEC: AnimationSpec<Float> = tween(BAND_ANIM_MILLIS)
