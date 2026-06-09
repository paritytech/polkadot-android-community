package io.paritytech.polkadotapp.app.root.presentation.main.compose

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.paritytech.polkadotapp.app.root.presentation.main.MainViewModel
import io.paritytech.polkadotapp.common.presentation.tabs.BottomTab
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.ChatFilled
import io.paritytech.polkadotapp.design.components.icon.vectors.GlobeAltFilled
import io.paritytech.polkadotapp.design.components.icon.vectors.SettingsFilled
import io.paritytech.polkadotapp.design.components.icon.vectors.WalletFilled
import io.paritytech.polkadotapp.design.components.navigationbar.LocalAppNavigationBarInsets
import io.paritytech.polkadotapp.design.components.navigationbar.PolkadotNavigationBar
import io.paritytech.polkadotapp.design.components.navigationbar.PolkadotNavigationBarItem
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_chats_impl.presentation.list.compose.ChatListScreen
import io.paritytech.polkadotapp.feature_products_impl.presentation.exploreProducts.compose.ExploreProductsScreen
import io.paritytech.polkadotapp.feature_settings_impl.presentation.main.SettingsScreen
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.PocketScreen
import io.paritytech.polkadotapp.common.R as RCommon

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onBottomNavHeightChanged: (Dp) -> Unit,
    onBottomNavDisposed: () -> Unit
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val tabWarnings by viewModel.tabWarnings.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose { onBottomNavDisposed() }
    }

    MainScreenInternal(
        currentTab = currentTab,
        tabWarnings = tabWarnings,
        onTabSelected = viewModel::selectTab,
        onBottomNavHeightChanged = onBottomNavHeightChanged
    )
}

@Composable
private fun MainScreenInternal(
    currentTab: BottomTab,
    tabWarnings: Map<BottomTab, Boolean>,
    onTabSelected: (BottomTab) -> Unit,
    onBottomNavHeightChanged: (Dp) -> Unit
) {
    val density = LocalDensity.current
    var navBarHeight by remember { mutableStateOf(0.dp) }
    val navBarInsets = WindowInsets(bottom = navBarHeight)

    LaunchedEffect(navBarHeight) {
        onBottomNavHeightChanged(navBarHeight)
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        CompositionLocalProvider(LocalAppNavigationBarInsets provides navBarInsets) {
            Crossfade(
                modifier = Modifier.fillMaxSize(),
                targetState = currentTab
            ) {
                when (it) {
                    BottomTab.CHATS -> ChatListScreen()
                    BottomTab.WALLET -> PocketScreen()
                    BottomTab.EXPLORE -> ExploreProductsScreen()
                    BottomTab.SETTINGS -> SettingsScreen()
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { size ->
                    navBarHeight = with(density) { size.height.toDp() }
                }
                .background(
                    Brush.verticalGradient(
                        listOf(
                            PolkadotTheme.colors.gradient.navigationOverlayEnd,
                            PolkadotTheme.colors.gradient.navigationOverlayStart
                        )
                    )
                )
                .padding(
                    vertical = PolkadotTheme.spacings.small,
                    horizontal = PolkadotTheme.spacings.extraMedium
                )
                .navigationBarsPadding()
        ) {
            PolkadotNavigationBar(
                selectedIndex = currentTab.ordinal,
                itemCount = BottomTab.entries.size
            ) {
                BottomTab.entries.fastForEach {
                    PolkadotNavigationBarItem(
                        selected = it == currentTab,
                        onClick = { onTabSelected(it) },
                        icon = it.icon(),
                        label = it.title(),
                        hasNotification = tabWarnings[it] == true
                    )
                }
            }
        }
    }
}

fun BottomTab.icon() = when (this) {
    BottomTab.CHATS -> NovaIcons.ChatFilled
    BottomTab.WALLET -> NovaIcons.WalletFilled
    BottomTab.EXPLORE -> NovaIcons.GlobeAltFilled
    BottomTab.SETTINGS -> NovaIcons.SettingsFilled
}

@Composable
fun BottomTab.title() = stringResource(
    when (this) {
        BottomTab.CHATS -> RCommon.string.bottom_nav_menu_chats
        BottomTab.WALLET -> RCommon.string.bottom_nav_menu_pocket
        BottomTab.EXPLORE -> RCommon.string.bottom_nav_menu_explore
        BottomTab.SETTINGS -> RCommon.string.bottom_nav_menu_settings
    }
)
