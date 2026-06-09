package io.paritytech.polkadotapp.feature_videogame_impl.presentation.play.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import io.paritytech.polkadotapp.design.components.icon.NovaIcon
import io.paritytech.polkadotapp.design.components.spacer.VerticalSpacer
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_videogame_impl.data.models.webrtc.PlayerConnectionState
import io.paritytech.polkadotapp.feature_videogame_impl.presentation.play.compose.icons.PlayerDisconnectedFilled
import io.paritytech.polkadotapp.feature_videogame_impl.presentation.play.models.PlayerUiModel
import io.paritytech.polkadotapp.feature_videogame_impl.presentation.play.models.VideoGameUiState
import io.paritytech.polkadotapp.feature_videogame_impl.presentation.play.models.findHost
import kotlinx.collections.immutable.ImmutableList
import io.paritytech.polkadotapp.common.R as RCommon

@Composable
fun HostUnavailableOverlay(state: VideoGameUiState, players: ImmutableList<PlayerUiModel>) {
    val isVisible = remember(state, players) {
        when (state) {
            is VideoGameUiState.Hosting -> {
                val host = players.findHost()

                host == null || host.connection != PlayerConnectionState.Connected
            }

            else -> false
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = {})
                .background(Color(0x73000000))
                .padding(PolkadotTheme.spacings.extraLargeIncreased),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            NovaIcon(
                imageVector = PlayerDisconnectedFilled,
                tint = PolkadotTheme.colors.fg.primary
            )

            VerticalSpacer { mediumIncreased }

            NovaText(
                text = stringResource(RCommon.string.video_game_play_disconnected_host_overlay),
                style = PolkadotTheme.typography.headline.small,
                color = PolkadotTheme.colors.fg.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}
