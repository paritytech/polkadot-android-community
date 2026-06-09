package io.paritytech.polkadotapp.design.components.qr

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import io.github.alexzhirkevich.qrose.options.QrBallShape
import io.github.alexzhirkevich.qrose.options.QrBrush
import io.github.alexzhirkevich.qrose.options.QrFrameShape
import io.github.alexzhirkevich.qrose.options.QrPixelShape
import io.github.alexzhirkevich.qrose.options.circle
import io.github.alexzhirkevich.qrose.options.roundCorners
import io.github.alexzhirkevich.qrose.options.solid
import io.github.alexzhirkevich.qrose.options.square
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import io.github.alexzhirkevich.qrose.toImageBitmap
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.theme.PolkadotTheme

private const val QR_EXPORT_SIZE = 512

@Composable
fun QrCode(
    modifier: Modifier = Modifier,
    text: String,
    onQrReady: ((Bitmap) -> Unit)? = null
) {
    val themeColors = PolkadotTheme.colors
    val painter = rememberQrCodePainter(text) {
        shapes {
            ball = QrBallShape.circle()
            frame = QrFrameShape.circle()
            darkPixel = QrPixelShape.circle(1f)
        }
        colors {
            ball = QrBrush.solid(themeColors.fg.primary)
            frame = QrBrush.solid(themeColors.fg.primary)
            dark = QrBrush.solid(themeColors.fg.primary)
        }
    }

    Box(modifier) {
        PolkadotSurface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            color = Color.Transparent
        ) {
            Image(
                painter = painter,
                contentDescription = "qr_image"
            )
        }
    }

    if (onQrReady != null) {
        // Separate painter for bitmap export — toImageBitmap() corrupts the display painter's internal cache
        val exportPainter = rememberQrCodePainter(text) {
            shapes {
                ball = QrBallShape.square()
                frame = QrFrameShape.roundCorners(0.3f)
                darkPixel = QrPixelShape.circle(0.6f)
            }
        }

        LaunchedEffect(text, exportPainter) {
            val exportSize = QR_EXPORT_SIZE

            val bitmap = createBitmap(exportSize, exportSize, Bitmap.Config.RGB_565).applyCanvas {
                drawColor(android.graphics.Color.WHITE)
                drawBitmap(
                    exportPainter.toImageBitmap(exportSize, exportSize).asAndroidBitmap(),
                    0f,
                    0f,
                    null
                )
            }
            onQrReady(bitmap)
        }
    }
}

@Preview
@Composable
private fun QrCodePreview() {
    PolkadotTheme {
        QrCode(
            text = "https://polkadot.app"
        )
    }
}
