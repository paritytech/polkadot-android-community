package io.paritytech.polkadotapp.feature_products_impl.presentation.spaBrowser.compose

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.paritytech.polkadotapp.design.components.bottomsheet.NovaModalBottomSheet
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.Close
import io.paritytech.polkadotapp.design.components.icon.vectors.More
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.components.topbar.PolkadotTopBar
import io.paritytech.polkadotapp.design.components.topbar.TopBarTitleAlignment
import io.paritytech.polkadotapp.design.components.topbar.rememberTopBarAction
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_products_impl.presentation.spaBrowser.SpaBrowserUiState
import io.paritytech.polkadotapp.feature_products_impl.presentation.spaBrowser.SpaBrowserViewModel
import io.paritytech.polkadotapp.feature_products_impl.presentation.spaBrowser.compose.components.BrowserMenuContent
import io.paritytech.polkadotapp.feature_products_impl.presentation.spaBrowser.compose.components.KioskPinOverlay
import kotlinx.collections.immutable.persistentListOf

private val EXIT_HOTSPOT_SIZE: Dp = 56.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaBrowserScreen(viewModel: SpaBrowserViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val webView by viewModel.webView.collectAsStateWithLifecycle()

    BackHandler {
        viewModel.onBackPressed()
    }

    ImmersiveModeEffect(enabled = state.kiosk.isEngaged)

    SpaBrowserScreenInternal(
        state = state,
        webView = webView,
        onCloseClick = viewModel::onCloseClick,
        onMoreClicked = viewModel::onMoreClicked,
        onKioskExitTap = viewModel::onKioskExitTap,
    )

    NovaModalBottomSheet(
        isVisible = state.isMoreMenuVisible,
        onDismissRequest = viewModel::onMoreMenuDismissed,
    ) {
        BrowserMenuContent(
            canOpenChat = state.canOpenChat,
            onDismiss = viewModel::onMoreMenuDismissed,
            onOpenChatClick = viewModel::onOpenChatClick,
            onRefreshClick = viewModel::onRefreshClick,
            onShareClick = viewModel::onShareClick,
            onKioskModeClick = viewModel::onKioskModeClicked,
        )
    }

    if (state.kiosk.isPromptVisible) {
        KioskPinOverlay(
            isSettingPin = state.kiosk.isSettingPin,
            enteredDigits = state.kiosk.enteredDigits,
            hasError = state.kiosk.hasError,
            onDigitClick = viewModel::onKioskPinDigit,
            onBackspaceClick = viewModel::onKioskPinBackspace,
            onCancelClick = viewModel::onKioskPinDismissed,
        )
    }
}

@Composable
private fun ImmersiveModeEffect(enabled: Boolean) {
    LocalActivity.current?.let { activity ->
        DisposableEffect(enabled) {
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            if (enabled) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            onDispose {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

@Composable
private fun SpaBrowserScreenInternal(
    state: SpaBrowserUiState,
    webView: WebView?,
    onCloseClick: () -> Unit,
    onMoreClicked: () -> Unit,
    onKioskExitTap: () -> Unit,
) {
    val isEngaged = state.kiosk.isEngaged
    PolkadotSurface(color = PolkadotTheme.colors.bg.surface.main) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = if (isEngaged) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                },
            ) {
                if (!isEngaged) {
                    PolkadotTopBar(
                        navigationAction = rememberTopBarAction(
                            action = onCloseClick,
                            icon = NovaIcons.Close,
                        ),
                        title = state.title.orEmpty(),
                        subtitle = state.subtitle.orEmpty(),
                        titleAlignment = TopBarTitleAlignment.Center,
                        actions = persistentListOf(
                            rememberTopBarAction(
                                action = onMoreClicked,
                                icon = NovaIcons.More,
                            ),
                        ),
                    )
                }
                PolkadotSurface(
                    modifier = Modifier.weight(1f),
                    color = PolkadotTheme.colors.bg.surface.container,
                ) {
                    if (webView != null) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { webView },
                        )
                    }
                }
            }

            if (state.kiosk.showExitHotspot) {
                ExitHotspot(
                    modifier = Modifier.align(Alignment.TopEnd),
                    onTap = onKioskExitTap,
                )
            }
        }
    }
}

@Composable
private fun ExitHotspot(
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(EXIT_HOTSPOT_SIZE)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onTap,
            ),
    )
}

@Preview
@Composable
private fun SpaBrowserScreenPreview() {
    PolkadotTheme {
        SpaBrowserScreenInternal(
            state = SpaBrowserUiState(
                title = "Web3 Summit App",
                subtitle = "web3summit.com",
            ),
            webView = null,
            onCloseClick = {},
            onMoreClicked = {},
            onKioskExitTap = {},
        )
    }
}
