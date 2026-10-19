package io.paritytech.polkadotapp.design.components.icon.vectors

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import io.paritytech.polkadotapp.design.components.icon.NovaIcons

val NovaIcons.ScanPlus: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "ScanPlus",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    )
        .addPath(
            pathData = addPathNodes(SCAN_PLUS_PATH),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.3f,
            strokeLineCap = StrokeCap.Round
        )
        .build()
}

private const val SCAN_PLUS_PATH = "M12 5.93V18.07M5.93 12H18.07"
