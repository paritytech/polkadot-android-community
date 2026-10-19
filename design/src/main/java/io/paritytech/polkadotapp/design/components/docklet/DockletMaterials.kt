package io.paritytech.polkadotapp.design.components.docklet

import android.graphics.Matrix
import android.os.Build
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.max

object DockletMaterials {
    val PillBlurSigma = 24.dp
    val DockBlurSigma = 10.dp
    val BehindCardBlurSigma = 8.dp
    val DockItemsBlurSigma = 3.dp
    const val PillSaturation = 1.6f
    const val DockSaturation = 1.5f
    const val BehindCardBrightness = 0.7f
    const val DockItemsFadedAlpha = 0.55f

    // hwui turns a blur radius into σ as 0.57735 · radius + 0.5, in px (frameworks/base libs/hwui/utils/Blur.cpp).
    private const val BlurSigmaScale = 0.57735f
    private const val BlurSigmaOffset = 0.5f

    fun backdropBlurSupported(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.S

    fun blurRadiusPx(sigmaDp: Float, density: Float): Float =
        max(0f, (sigmaDp * density - BlurSigmaOffset) / BlurSigmaScale)
}

private val GlassFillTop = DockletColors.GlassLight.copy(alpha = DockletColors.GlassFillAlpha)
private val GlassFillBottom = DockletColors.GlassDark.copy(alpha = DockletColors.GlassFillAlpha)
private const val DockFillWidthShare = 0.45f
private const val RimEndAlpha = 0.03f
private const val RimEndFade = 0.08f
private const val ArcAlpha = 0.3f
private const val ArcStartAngle = 35f
private const val ArcSweep = 110f
private const val HighlightAlpha = 0.1f
private const val HighlightLineAlpha = 0.08f
private const val FieldLineAlpha = 0.18f
private const val FieldOutlineAlpha = 0.08f
private val RimOffsets = floatArrayOf(0f, 0.05f, 0.12f, 0.5f, 0.86f, 0.95f, 1f)
private val DockRimAlphas = floatArrayOf(0.55f, 0.3f, 0.08f, 0.05f, 0.06f, 0.24f, 0.36f)
private val PillRimAlphas = floatArrayOf(0.62f, 0.42f, 0.1f, 0.04f, 0.06f, 0.28f, 0.44f)
private val GlintAlphas = floatArrayOf(0.5f, 0.12f, 0.03f, 0.05f, 0.42f, 0.2f, 0.08f)
private val GlintOffsets = FloatArray(GlintAlphas.size) { it / (GlintAlphas.size - 1f) }
private val LineWidth = 1.dp

fun dockletBlurEffect(sigma: Dp, density: Float): BlurEffect? {
    if (!DockletMaterials.backdropBlurSupported(Build.VERSION.SDK_INT)) return null

    val radius = DockletMaterials.blurRadiusPx(sigma.value, density)
    return if (radius > 0f) BlurEffect(radius, radius, TileMode.Decal) else null
}

internal fun DrawScope.drawDockGlass(path: Path, cutout: Path, base: Color) {
    drawPath(path, base)
    val radialRadius = size.width * DockFillWidthShare
    drawPath(path, ellipticalRadialBrush(radialRadius, size.height, size.width / 2))
    drawInnerLine(path, rimBrush(DockRimAlphas))
    drawInnerLine(path, rimEndsBrush())
    clipPath(path) {
        val left = size.width / 2 - DockletGeometry.CutoutHalfWidth.toPx()
        val right = size.width / 2 + DockletGeometry.CutoutHalfWidth.toPx()
        drawPath(
            path = cutout,
            brush = Brush.horizontalGradient(colorStops = whiteStops(GlintAlphas, GlintOffsets), startX = left, endX = right),
            style = Stroke(width = LineWidth.toPx() * 2),
        )
    }
}

internal fun DrawScope.drawCardGlass(
    path: Path,
    fillBlend: Float,
    pillArcAlpha: Float,
    pillCentre: Offset,
    base: Color,
    cardFill: Color,
) {
    drawPath(path, base)
    if (fillBlend < 1f) {
        drawPath(path, Brush.verticalGradient(listOf(GlassFillTop, GlassFillBottom)), alpha = 1f - fillBlend)
    }
    if (fillBlend > 0f) {
        drawPath(path, cardFill, alpha = fillBlend)
    }
    val rimAlphas = FloatArray(RimOffsets.size) { lerp(PillRimAlphas[it], DockRimAlphas[it], fillBlend) }
    drawInnerLine(path, rimBrush(rimAlphas))
    if (pillArcAlpha > 0f) {
        val radius = DockletGeometry.PillDiameter.toPx() / 2 + LineWidth.toPx() / 2
        drawArc(
            color = DockletColors.Shade.copy(alpha = ArcAlpha * pillArcAlpha),
            startAngle = ArcStartAngle,
            sweepAngle = ArcSweep,
            useCenter = false,
            topLeft = pillCentre - Offset(radius, radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = LineWidth.toPx()),
        )
    }
}

fun Modifier.dockletHighlight(color: Color): Modifier = drawWithCache {
    val outline = CircleShape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val line = topLine(path, LineWidth.toPx())
    onDrawBehind {
        drawPath(path, color.copy(alpha = HighlightAlpha))
        drawPath(line, color.copy(alpha = HighlightLineAlpha))
    }
}

fun Modifier.dockletFieldOutline(shape: Shape): Modifier = drawWithCache {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val line = topLine(path, LineWidth.toPx())
    onDrawWithContent {
        drawContent()
        drawPath(line, DockletColors.Light.copy(alpha = FieldLineAlpha))
        drawInnerLine(path, SolidColor(DockletColors.Light.copy(alpha = FieldOutlineAlpha)))
    }
}

private fun DrawScope.drawInnerLine(path: Path, brush: Brush) {
    clipPath(path) {
        drawPath(path = path, brush = brush, style = Stroke(width = LineWidth.toPx() * 2))
    }
}

private fun topLine(path: Path, width: Float): Path {
    val shifted = Path().apply { addPath(path, Offset(0f, width)) }
    return Path().apply { op(path, shifted, PathOperation.Difference) }
}

private fun DrawScope.rimBrush(alphas: FloatArray): Brush {
    val reach = (size.width + size.height) / 2
    return Brush.linearGradient(colorStops = whiteStops(alphas, RimOffsets), start = Offset.Zero, end = Offset(reach, reach))
}

private fun DrawScope.rimEndsBrush(): Brush = Brush.horizontalGradient(
    0f to DockletColors.Light.copy(alpha = RimEndAlpha),
    RimEndFade to Color.Transparent,
    1f - RimEndFade to Color.Transparent,
    1f to DockletColors.Light.copy(alpha = RimEndAlpha),
    startX = 0f,
    endX = size.width,
)

private fun whiteStops(alphas: FloatArray, offsets: FloatArray): Array<Pair<Float, Color>> =
    Array(alphas.size) { index -> offsets[index] to DockletColors.Light.copy(alpha = alphas[index]) }

private fun ellipticalRadialBrush(radiusX: Float, radiusY: Float, centreX: Float): Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader =
        RadialGradientShader(
            center = Offset(centreX, 0f),
            radius = radiusX,
            colors = listOf(GlassFillTop, GlassFillBottom),
        ).apply {
            setLocalMatrix(Matrix().apply { setScale(1f, radiusY / radiusX, centreX, 0f) })
        }
}
