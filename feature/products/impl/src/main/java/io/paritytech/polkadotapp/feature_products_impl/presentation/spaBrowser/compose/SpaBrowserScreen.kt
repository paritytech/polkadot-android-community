package io.paritytech.polkadotapp.feature_products_impl.presentation.spaBrowser.compose

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
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
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaBrowserScreen(viewModel: SpaBrowserViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val webView by viewModel.webView.collectAsStateWithLifecycle()

    BackHandler {
        viewModel.onBackPressed()
    }

    SpaBrowserScreenInternal(
        state = state,
        webView = webView,
        onCloseClick = viewModel::onCloseClick,
        onMoreClicked = viewModel::onMoreClicked,
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
        )
    }
}

@Composable
private fun SpaBrowserScreenInternal(
    state: SpaBrowserUiState,
    webView: WebView?,
    onCloseClick: () -> Unit,
    onMoreClicked: () -> Unit,
) {
    PolkadotSurface(color = PolkadotTheme.colors.bg.surface.main) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            PolkadotTopBar(
                navigationAction = rememberTopBarAction(
                    action = onCloseClick,
                    icon = NovaIcons.Close
                ),
                title = state.title.orEmpty(),
                subtitle = state.subtitle.orEmpty(),
                titleAlignment = TopBarTitleAlignment.Center,
                actions = persistentListOf(
                    rememberTopBarAction(
                        action = onMoreClicked,
                        icon = NovaIcons.More
                    ),
                ),
            )
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
    }
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
        )
    }
}
