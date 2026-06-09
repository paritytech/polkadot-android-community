package io.paritytech.polkadotapp.app.root.presentation.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.paritytech.polkadotapp.app.root.presentation.main.compose.MainScreen
import io.paritytech.polkadotapp.common.presentation.formatters.time.LocalTimeFormatter
import io.paritytech.polkadotapp.common.presentation.formatters.time.TimeFormatter
import io.paritytech.polkadotapp.common.presentation.screens.BaseComposeFragment
import io.paritytech.polkadotapp.feature_chats_impl.presentation.formatter.ChatMessageTimeFormatter
import io.paritytech.polkadotapp.feature_chats_impl.presentation.formatter.LocalChatMessageTimeFormatter
import io.paritytech.polkadotapp.feature_prices_api.presentation.formatter.FiatFormatter
import io.paritytech.polkadotapp.feature_prices_api.presentation.formatter.LocalFiatFormatter
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.LocalTokenAmountFormatter
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.TokenAmountFormatter
import javax.inject.Inject

@AndroidEntryPoint
class MainFragment : BaseComposeFragment<MainViewModel>() {
    override val viewModel by viewModels<MainViewModel>()

    @Inject
    lateinit var chatMessageTimeFormatter: ChatMessageTimeFormatter

    @Inject
    lateinit var timeFormatter: TimeFormatter

    @Inject
    lateinit var tokenAmountFormatter: TokenAmountFormatter

    @Inject
    lateinit var fiatFormatter: FiatFormatter

    @Inject
    lateinit var bottomNavHeightProvider: BottomNavHeightProvider

    @Composable
    override fun Screen() {
        CompositionLocalProvider(
            LocalChatMessageTimeFormatter provides chatMessageTimeFormatter,
            LocalTimeFormatter provides timeFormatter,
            LocalTokenAmountFormatter provides tokenAmountFormatter,
            LocalFiatFormatter provides fiatFormatter
        ) {
            MainScreen(
                viewModel = viewModel,
                onBottomNavHeightChanged = bottomNavHeightProvider::set,
                onBottomNavDisposed = bottomNavHeightProvider::clear
            )
        }
    }
}
