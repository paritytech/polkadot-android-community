package io.paritytech.polkadotapp.feature_products_impl.presentation.spaBrowser.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonStyle
import io.paritytech.polkadotapp.design.components.button.default.PolkadotTextButton
import io.paritytech.polkadotapp.design.components.icon.NovaIcon
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.ArrowLeft
import io.paritytech.polkadotapp.design.components.spacer.VerticalSpacer
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.common.R as RCommon

private const val PIN_LENGTH = 4
private val KEY_SIZE: Dp = 72.dp
private val DOT_SIZE: Dp = 14.dp

@Composable
fun KioskPinOverlay(
    modifier: Modifier = Modifier,
    isSettingPin: Boolean,
    enteredDigits: Int,
    hasError: Boolean,
    onDigitClick: (Int) -> Unit,
    onBackspaceClick: () -> Unit,
    onCancelClick: () -> Unit,
) {
    PolkadotSurface(
        modifier = modifier.fillMaxSize(),
        color = PolkadotTheme.colors.bg.surface.main,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = PolkadotTheme.spacings.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            NovaText(
                text = stringResource(
                    if (isSettingPin) {
                        RCommon.string.kiosk_set_pin_title
                    } else {
                        RCommon.string.kiosk_enter_pin_title
                    },
                ),
                style = PolkadotTheme.typography.title.medium,
                color = PolkadotTheme.colors.fg.primary,
                textAlign = TextAlign.Center,
            )

            VerticalSpacer { extraLarge }

            PinDots(enteredDigits = enteredDigits, hasError = hasError)

            VerticalSpacer { mediumIncreased }

            NovaText(
                text = if (hasError) stringResource(RCommon.string.kiosk_wrong_pin) else "",
                style = PolkadotTheme.typography.body.small,
                color = PolkadotTheme.colors.fg.error,
                textAlign = TextAlign.Center,
            )

            VerticalSpacer { extraLarge }

            Keypad(
                onDigitClick = onDigitClick,
                onBackspaceClick = onBackspaceClick,
            )

            VerticalSpacer { extraLarge }

            PolkadotTextButton(
                modifier = Modifier.fillMaxWidth(),
                style = PolkadotButtonStyle.ghost(),
                text = stringResource(RCommon.string.common_cancel),
                onClick = onCancelClick,
            )
        }
    }
}

@Composable
private fun PinDots(
    enteredDigits: Int,
    hasError: Boolean,
) {
    val emptyColor = if (hasError) {
        PolkadotTheme.colors.fg.error
    } else {
        PolkadotTheme.colors.fg.tertiary
    }
    Row(horizontalArrangement = Arrangement.spacedBy(PolkadotTheme.spacings.mediumIncreased)) {
        repeat(PIN_LENGTH) { index ->
            val isFilled = index < enteredDigits
            PolkadotSurface(
                modifier = Modifier.size(DOT_SIZE),
                shape = CircleShape,
                color = if (isFilled) PolkadotTheme.colors.fg.primary else emptyColor,
            ) {}
        }
    }
}

@Composable
private fun Keypad(
    onDigitClick: (Int) -> Unit,
    onBackspaceClick: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(PolkadotTheme.spacings.mediumIncreased),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        KeypadRow {
            DigitKey(digit = 1, onClick = onDigitClick)
            DigitKey(digit = 2, onClick = onDigitClick)
            DigitKey(digit = 3, onClick = onDigitClick)
        }
        KeypadRow {
            DigitKey(digit = 4, onClick = onDigitClick)
            DigitKey(digit = 5, onClick = onDigitClick)
            DigitKey(digit = 6, onClick = onDigitClick)
        }
        KeypadRow {
            DigitKey(digit = 7, onClick = onDigitClick)
            DigitKey(digit = 8, onClick = onDigitClick)
            DigitKey(digit = 9, onClick = onDigitClick)
        }
        KeypadRow {
            Box(modifier = Modifier.size(KEY_SIZE))
            DigitKey(digit = 0, onClick = onDigitClick)
            BackspaceKey(onClick = onBackspaceClick)
        }
    }
}

@Composable
private fun KeypadRow(content: @Composable RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(PolkadotTheme.spacings.mediumIncreased),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun DigitKey(
    digit: Int,
    onClick: (Int) -> Unit,
) {
    PolkadotSurface(
        modifier = Modifier.size(KEY_SIZE),
        shape = CircleShape,
        color = PolkadotTheme.colors.bg.surface.container,
        onClick = { onClick(digit) },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            NovaText(
                text = digit.toString(),
                style = PolkadotTheme.typography.title.large,
                color = PolkadotTheme.colors.fg.primary,
            )
        }
    }
}

@Composable
private fun BackspaceKey(onClick: () -> Unit) {
    PolkadotSurface(
        modifier = Modifier.size(KEY_SIZE),
        shape = CircleShape,
        color = Color.Transparent,
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            NovaIcon(
                imageVector = NovaIcons.ArrowLeft,
                tint = PolkadotTheme.colors.fg.primary,
            )
        }
    }
}

@Preview
@Composable
private fun KioskPinOverlaySettingPreview() {
    PolkadotTheme {
        KioskPinOverlay(
            isSettingPin = true,
            enteredDigits = 2,
            hasError = false,
            onDigitClick = {},
            onBackspaceClick = {},
            onCancelClick = {},
        )
    }
}

@Preview
@Composable
private fun KioskPinOverlayErrorPreview() {
    PolkadotTheme {
        KioskPinOverlay(
            isSettingPin = false,
            enteredDigits = 0,
            hasError = true,
            onDigitClick = {},
            onBackspaceClick = {},
            onCancelClick = {},
        )
    }
}
