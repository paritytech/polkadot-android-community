package io.paritytech.polkadotapp.feature_settings_impl.presentation.forceReclaim

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.paritytech.polkadotapp.common.presentation.screens.BaseComposeFragment
import io.paritytech.polkadotapp.feature_settings_impl.presentation.forceReclaim.compose.ForceReclaimScreen
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.LocalTokenAmountFormatter
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.TokenAmountFormatter
import javax.inject.Inject

@AndroidEntryPoint
class ForceReclaimFragment : BaseComposeFragment<ForceReclaimViewModel>() {
    @Inject
    lateinit var tokenAmountFormatter: TokenAmountFormatter

    override val viewModel: ForceReclaimViewModel by viewModels()

    @Composable
    override fun Screen() = CompositionLocalProvider(
        LocalTokenAmountFormatter provides tokenAmountFormatter
    ) {
        ForceReclaimScreen(contract = viewModel)
    }
}
