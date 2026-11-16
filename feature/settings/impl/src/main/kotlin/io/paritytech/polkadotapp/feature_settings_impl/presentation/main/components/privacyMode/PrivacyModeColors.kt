package io.paritytech.polkadotapp.feature_settings_impl.presentation.main.components.privacyMode

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

@Immutable
internal data class CircleColors(
    val fillTop: Color,
    val fillBottom: Color,
    val rimTop: Color,
    val rimBottom: Color
)

internal fun CircleColors.blendedTo(other: CircleColors, fraction: Float): CircleColors = CircleColors(
    fillTop = lerp(fillTop, other.fillTop, fraction),
    fillBottom = lerp(fillBottom, other.fillBottom, fraction),
    rimTop = lerp(rimTop, other.rimTop, fraction),
    rimBottom = lerp(rimBottom, other.rimBottom, fraction)
)

@Immutable
internal data class ModeColors(
    val selected: CircleColors,
    val unselected: CircleColors,
    val glow: Color
)

// The design's per-mode shades; none of them exists as a palette token.
internal object PrivacyModeColors {
    val Glyph = Color(0xFFF4F4F5)

    val Fastest = ModeColors(
        selected = CircleColors(
            fillTop = Color(0xFFF7C66A),
            fillBottom = Color(0xFFB8862A),
            rimTop = Color(0xFFF5B338),
            rimBottom = Color(0xFFF5B338)
        ),
        unselected = CircleColors(
            fillTop = Color(0xFF7C591E),
            fillBottom = Color(0xFF7C591E),
            rimTop = Color(0xFFCFA155),
            rimBottom = Color(0xFF5B3C07)
        ),
        glow = Color(0xFFF5B338)
    )

    val Balanced = ModeColors(
        selected = CircleColors(
            fillTop = Color(0xFF7EEC6A),
            fillBottom = Color(0xFF10AA49),
            rimTop = Color(0xFFC9FFC0),
            rimBottom = Color(0xFF138200)
        ),
        unselected = CircleColors(
            fillTop = Color(0xFF5A9A4F),
            fillBottom = Color(0xFF21693C),
            rimTop = Color(0xFF90BA89),
            rimBottom = Color(0xFF90BA89)
        ),
        glow = Color(0xFF7EEC6A)
    )

    val MostPrivate = ModeColors(
        selected = CircleColors(
            fillTop = Color(0xFF9D6BF1),
            fillBottom = Color(0xFF5D2BB2),
            rimTop = Color(0xFF7C3AED),
            rimBottom = Color(0xFF7C3AED)
        ),
        unselected = CircleColors(
            fillTop = Color(0xFF503085),
            fillBottom = Color(0xFF503085),
            rimTop = Color(0xFF876CB4),
            rimBottom = Color(0xFF28134A)
        ),
        glow = Color(0xFF7C3AED)
    )
}
