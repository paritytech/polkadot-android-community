package io.paritytech.polkadotapp.feature_products_impl.presentation.spaSheet

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.paritytech.polkadotapp.common.presentation.screens.BaseComposeBottomSheet
import io.paritytech.polkadotapp.feature_products_impl.presentation.spaSheet.compose.SpaSheetScreen

@AndroidEntryPoint
class SpaSheetBottomSheet : BaseComposeBottomSheet<SpaSheetViewModel>() {
    override val viewModel: SpaSheetViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // The hosted product scrolls its own content; leaving the sheet draggable steals every
        // upward gesture, so the product can be scrolled down but never back up.
        bottomSheetBehavior?.isDraggable = false
    }

    @Composable
    override fun Screen() = SpaSheetScreen(viewModel)

    override fun onPause() {
        super.onPause()
        viewModel.pauseConnections()
    }

    override fun onResume() {
        super.onResume()
        viewModel.resumeConnections()
    }
}
