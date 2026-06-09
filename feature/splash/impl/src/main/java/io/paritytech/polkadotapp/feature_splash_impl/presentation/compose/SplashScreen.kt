package io.paritytech.polkadotapp.feature_splash_impl.presentation.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.paritytech.polkadotapp.design.components.progress.LoadingScreenState
import io.paritytech.polkadotapp.design.components.spacer.VerticalSpacer
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_splash_impl.presentation.SplashViewModel
import io.paritytech.polkadotapp.feature_splash_impl.presentation.compose.components.icons.PolkadotLogo

@Composable
fun SplashScreen(viewModel: SplashViewModel) {
    val waitingForNetworkVisible by viewModel.waitingForNetworkVisible.collectAsStateWithLifecycle()

    PolkadotSurface {
        AnimatedContent(
            modifier = Modifier.fillMaxSize(),
            targetState = waitingForNetworkVisible
        ) { waitingForNetwork ->
            if (waitingForNetwork) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(PolkadotTheme.spacings.mediumIncreased),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        imageVector = PolkadotLogo,
                        contentDescription = "logo",
                        colorFilter = ColorFilter.tint(PolkadotTheme.colors.fg.primary)
                    )

                    VerticalSpacer { mediumIncreased }

                    NovaText(
                        text = "Waiting for network connection...",
                        style = PolkadotTheme.typography.title.medium,
                        color = PolkadotTheme.colors.fg.secondary
                    )

                    VerticalSpacer { tiny }

                    NovaText(
                        text = "Turn off Airplane Mode or check your internet",
                        style = PolkadotTheme.typography.body.medium,
                        color = PolkadotTheme.colors.fg.secondary
                    )
                }
            } else {
                LoadingScreenState()
            }
        }
    }
}
