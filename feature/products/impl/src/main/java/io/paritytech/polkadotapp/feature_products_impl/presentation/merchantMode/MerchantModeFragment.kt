package io.paritytech.polkadotapp.feature_products_impl.presentation.merchantMode

import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.paritytech.polkadotapp.common.presentation.screens.BaseComposeFragment
import io.paritytech.polkadotapp.feature_products_impl.presentation.merchantMode.compose.MerchantModeScreen

@AndroidEntryPoint
class MerchantModeFragment : BaseComposeFragment<MerchantModeViewModel>() {
    override val viewModel: MerchantModeViewModel by viewModels()

    @Composable
    override fun Screen() = MerchantModeScreen(viewModel)

    override fun onPause() {
        super.onPause()
        viewModel.pauseConnections()
    }

    override fun onResume() {
        super.onResume()
        viewModel.resumeConnections()
    }
}
