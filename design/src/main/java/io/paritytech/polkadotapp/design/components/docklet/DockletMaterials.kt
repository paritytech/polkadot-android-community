package io.paritytech.polkadotapp.design.components.docklet

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

object DockletMaterials {
    val PillBlurSigma = 24.dp
    val DockBlurSigma = 10.dp
    val BehindCardBlurSigma = 8.dp
    val DockItemsBlurSigma = 3.dp
    const val BehindCardBrightness = 0.7f
    const val DockItemsFadedAlpha = 0.55f

    // hwui turns a blur radius into σ as 0.57735 · radius + 0.5, in px (frameworks/base libs/hwui/utils/Blur.cpp).
    private const val BlurSigmaScale = 0.57735f
    private const val BlurSigmaOffset = 0.5f

    fun backdropBlurSupported(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.S

    fun blurRadiusPx(sigmaDp: Float, density: Float): Float =
        max(0f, (sigmaDp * density - BlurSigmaOffset) / BlurSigmaScale)
}

private val LineWidth = 1.dp

fun dockletBlurEffect(sigma: Dp, density: Float): BlurEffect? {
    if (!DockletMaterials.backdropBlurSupported(Build.VERSION.SDK_INT)) return null

    val radius = DockletMaterials.blurRadiusPx(sigma.value, density)
    return if (radius > 0f) BlurEffect(radius, radius, TileMode.Decal) else null
}

internal fun DrawScope.drawDockletGlass(path: Path, palette: DockletPalette) {
    drawPath(path, palette.glassFill)
    drawInnerLine(path, palette.glassStroke)
}

fun Modifier.dockletFieldOutline(shape: Shape, palette: DockletPalette): Modifier = drawWithCache {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val line = topLine(path, LineWidth.toPx())
    onDrawWithContent {
        drawContent()
        drawPath(line, palette.fieldLine)
        drawInnerLine(path, palette.fieldOutline)
    }
}

private fun DrawScope.drawInnerLine(path: Path, color: Color) {
    clipPath(path) {
        drawPath(path = path, color = color, style = Stroke(width = LineWidth.toPx() * 2))
    }
}

private fun topLine(path: Path, width: Float): Path {
    val shifted = Path().apply { addPath(path, Offset(0f, width)) }
    return Path().apply { op(path, shifted, PathOperation.Difference) }
}
