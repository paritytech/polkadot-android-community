package io.paritytech.polkadotapp.feature_settings_impl.presentation.printer

import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.paritytech.polkadotapp.common.presentation.screens.BaseComposeFragment
import io.paritytech.polkadotapp.feature_settings_impl.presentation.printer.compose.PrinterDiagnosticsScreen

@AndroidEntryPoint
class PrinterDiagnosticsFragment : BaseComposeFragment<PrinterDiagnosticsViewModel>() {
    override val viewModel: PrinterDiagnosticsViewModel by viewModels()

    @Composable
    override fun Screen() {
        PrinterDiagnosticsScreen(viewModel)
    }
}
