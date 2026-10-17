package io.paritytech.polkadotapp.design.components.docklet

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.paritytech.polkadotapp.designsystem.typography.PolkadotFontFamilies

object DockletTypography {
    val FieldPlaceholder = TextStyle(
        fontFamily = PolkadotFontFamilies.inter,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )

    val Query = TextStyle(
        fontFamily = PolkadotFontFamilies.inter,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    )

    val SectionHeader = TextStyle(
        fontFamily = PolkadotFontFamilies.inter,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.06.em,
    )

    val RowTitle = TextStyle(
        fontFamily = PolkadotFontFamilies.inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    )

    val AvatarInitial = TextStyle(
        fontFamily = PolkadotFontFamilies.inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
    )
}
