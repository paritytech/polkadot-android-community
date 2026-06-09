package io.paritytech.polkadotapp.feature_videogame_impl.presentation.play.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonColors
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonShape
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonStyle
import io.paritytech.polkadotapp.design.components.button.default.PolkadotButtonSize
import io.paritytech.polkadotapp.design.components.button.default.PolkadotTextButton
import io.paritytech.polkadotapp.design.components.button.icon.PolkadotIconButton
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.ArrowDropdown
import io.paritytech.polkadotapp.design.components.icon.vectors.HelpOutlined
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_videogame_impl.presentation.compose.theme.GameColors
import io.paritytech.polkadotapp.common.R as RCommon

@Composable
fun GameTopBar(
    modifier: Modifier = Modifier,
    onAction: () -> Unit,
    content: (@Composable BoxScope.() -> Unit)? = null
) {
    Box(
        modifier = modifier,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PolkadotTheme.spacings.small)
        ) {
            // Chevron-down — tapping collapses the game screen into the persistent pill
            PolkadotIconButton(
                icon = NovaIcons.ArrowDropdown,
                onClick = onAction,
                style = gameTopBarButtonStyle(),
                shape = PolkadotButtonShape.pill
            )

            content?.invoke(this)
        }
    }
}

@Composable
private fun gameTopBarButtonStyle(): PolkadotButtonStyle = remember {
    GameButtonStyle(
        colors = PolkadotButtonColors(
            containerBrush = SolidColor(GameColors.gameTopBarBackground),
            contentColor = GameColors.textOnGameBackground,
            disabledContainerBrush = SolidColor(GameColors.gameTopBarBackground),
            disabledContentColor = GameColors.textOnGameBackground
        ),
        rippleColor = GameColors.textOnGameBackground
    )
}

@Composable
fun HowToPlayButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    PolkadotTextButton(
        text = stringResource(RCommon.string.video_game_how_to_play),
        onClick = onClick,
        modifier = modifier,
        style = PolkadotButtonStyle.secondary(),
        size = howToPlayButtonSize(),
        shape = PolkadotButtonShape.pill,
        iconEnd = NovaIcons.HelpOutlined
    )
}

@Composable
private fun howToPlayButtonSize(): PolkadotButtonSize {
    val padding = PaddingValues(
        start = PolkadotTheme.spacings.mediumIncreased,
        top = PolkadotTheme.spacings.small,
        end = PolkadotTheme.spacings.extraMedium,
        bottom = PolkadotTheme.spacings.small
    )
    val textStyle = PolkadotTheme.typography.title.medium
    return remember(padding, textStyle) { GameButtonSize(padding, textStyle) }
}

private data class GameButtonStyle(
    override val colors: PolkadotButtonColors,
    override val rippleColor: Color
) : PolkadotButtonStyle

private data class GameButtonSize(
    override val padding: PaddingValues,
    override val textStyle: TextStyle
) : PolkadotButtonSize

@Preview
@Composable
private fun GameTopBarPreview() {
    PolkadotTheme {
        GameTopBar(onAction = {})
    }
}
