package io.paritytech.polkadotapp.design.components.docklet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonShape
import io.paritytech.polkadotapp.design.components.button.default.PolkadotButton
import io.paritytech.polkadotapp.design.components.button.default.PolkadotButtonSize
import io.paritytech.polkadotapp.design.components.icon.NovaIcon
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.Close
import io.paritytech.polkadotapp.design.components.icon.vectors.Search
import io.paritytech.polkadotapp.design.components.spacer.HorizontalSpacer
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.components.text.PolkadotInputField
import io.paritytech.polkadotapp.design.theme.PolkadotTheme

private val CaretBrush = SolidColor(DockletColors.Caret)

private val ButtonSearchIconSize = 20.dp
private val FieldSearchIconSize = 22.dp
private val ClearTouchTarget = 32.dp
private val ClearCircleSize = 22.dp
private val ClearGlyphSize = 16.dp

@Composable
fun DockletSearchButton(
    modifier: Modifier,
    text: String,
    onClick: () -> Unit,
) {
    val shape = PolkadotButtonShape.pill
    Box(modifier = modifier) {
        PolkadotButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(DockletGeometry.FieldHeight)
                .dockletFieldOutline(shape),
            style = DockletFieldButtonStyle,
            size = PolkadotButtonSize.large(),
            shape = shape,
            content = { SearchButtonContent(text) },
        )
    }
}

@Composable
private fun SearchButtonContent(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        NovaIcon(
            modifier = Modifier.size(ButtonSearchIconSize),
            imageVector = NovaIcons.Search,
            tint = DockletColors.SearchIcon,
        )
        HorizontalSpacer { tiny }
        NovaText(
            text = text,
            style = DockletTypography.FieldPlaceholder,
            color = DockletColors.Placeholder,
            maxLines = 1,
        )
    }
}

@Composable
fun DockletSearchField(
    modifier: Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    Box(modifier = modifier) {
        SearchFieldRow(value, onValueChange, placeholder)
    }
}

@Composable
private fun SearchFieldRow(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val exclusion = LocalDockletDragExclusion.current
    DisposableEffect(exclusion) {
        onDispose { exclusion?.bounds = null }
    }
    val shape = PolkadotButtonShape.pill
    val horizontalPadding = PolkadotTheme.spacings.smallIncreased
    PolkadotSurface(
        modifier = Modifier
            .fillMaxWidth()
            .dockletDragExclusion(exclusion)
            .height(DockletGeometry.FieldHeight)
            .dockletFieldOutline(shape),
        shape = shape,
        color = DockletColors.FieldFill,
    ) {
        SearchFieldContent(value, onValueChange, placeholder, horizontalPadding)
    }
}

@Composable
private fun SearchFieldContent(value: String, onValueChange: (String) -> Unit, placeholder: String, horizontalPadding: Dp) {
    Row(
        modifier = Modifier.padding(start = horizontalPadding, end = horizontalPadding - (ClearTouchTarget - ClearCircleSize) / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NovaIcon(
            modifier = Modifier.size(FieldSearchIconSize),
            imageVector = NovaIcons.Search,
            tint = DockletColors.SearchIcon,
        )
        HorizontalSpacer { tiny }
        PolkadotInputField(
            modifier = Modifier.weight(1f),
            value = value,
            onValueChange = onValueChange,
            textStyle = DockletTypography.Query.copy(color = PolkadotTheme.colors.fg.primary),
            cursorBrush = CaretBrush,
            placeholder = {
                NovaText(
                    text = placeholder,
                    style = DockletTypography.Query,
                    color = DockletColors.Placeholder,
                    maxLines = 1,
                )
            },
        )
        if (value.isNotEmpty()) {
            ClearButton(onClick = { onValueChange("") })
        }
    }
}

@Composable
private fun ClearButton(onClick: () -> Unit) {
    PolkadotSurface(
        modifier = Modifier.size(ClearTouchTarget),
        shape = CircleShape,
        color = Color.Transparent,
        contentAlignment = Alignment.Center,
        onClick = onClick,
    ) {
        PolkadotSurface(
            modifier = Modifier.requiredSize(ClearCircleSize),
            shape = CircleShape,
            color = DockletColors.ClearBackground,
            contentAlignment = Alignment.Center,
        ) {
            NovaIcon(
                modifier = Modifier.requiredSize(ClearGlyphSize),
                imageVector = NovaIcons.Close,
                tint = DockletColors.ClearGlyph,
            )
        }
    }
}

@Preview
@Composable
private fun DockletSearchFieldPreview() {
    PolkadotTheme {
        Box(modifier = Modifier.background(DockletColors.CardFill)) {
            DockletSearchField(
                modifier = Modifier.fillMaxWidth(),
                value = "mo",
                onValueChange = {},
                placeholder = "",
            )
        }
    }
}
