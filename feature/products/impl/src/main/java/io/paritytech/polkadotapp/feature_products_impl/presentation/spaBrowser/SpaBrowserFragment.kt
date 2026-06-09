package io.paritytech.polkadotapp.feature_products_impl.presentation.spaBrowser

import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.paritytech.polkadotapp.common.presentation.screens.BaseComposeFragment
import io.paritytech.polkadotapp.feature_products_impl.presentation.spaBrowser.compose.SpaBrowserScreen

@AndroidEntryPoint
class SpaBrowserFragment : BaseComposeFragment<SpaBrowserViewModel>() {
    override val viewModel: SpaBrowserViewModel by viewModels()

    @Composable
    override fun Screen() {
        SpaBrowserScreen(viewModel)
    }

    override fun onPause() {
        super.onPause()
        viewModel.pauseConnections()
    }

    override fun onResume() {
        super.onResume()
        viewModel.resumeConnections()
    }
}
