package io.paritytech.polkadotapp.feature_settings_impl.presentation.printer

import dagger.hilt.android.lifecycle.HiltViewModel
import io.paritytech.polkadotapp.common.domain.printing.ReceiptPrinter
import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import io.paritytech.polkadotapp.feature_settings_impl.SettingsRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrinterDiagnosticsViewModel @Inject constructor(
    private val receiptPrinter: ReceiptPrinter,
    private val router: SettingsRouter,
) : BaseViewModel() {
    private val isPrinterAvailable = MutableStateFlow(receiptPrinter.isAvailable())
    private val isPrinterTestInProgress = MutableStateFlow(false)
    private val printerTestMessage = MutableStateFlow<PrinterTestMessage?>(null)
    private var printerTestMessageId = 0L

    val state: StateFlow<PrinterDiagnosticsState> = combine(
        isPrinterAvailable,
        isPrinterTestInProgress,
        printerTestMessage,
    ) { isPrinterAvailable, isPrinterTestInProgress, printerTestMessage ->
        PrinterDiagnosticsState(
            isPrinterAvailable = isPrinterAvailable,
            isPrinterTestInProgress = isPrinterTestInProgress,
            printerTestMessage = printerTestMessage,
        )
    }.stateIn(
        scope = this,
        started = SharingStarted.Eagerly,
        initialValue = PrinterDiagnosticsState(isPrinterAvailable = receiptPrinter.isAvailable())
    )

    fun onBackClick() {
        router.back()
    }

    fun refreshPrinterStatus() {
        isPrinterAvailable.value = receiptPrinter.isAvailable()
    }

    fun onPrintTestClick() {
        refreshPrinterStatus()
        if (!isPrinterAvailable.value) return
        if (isPrinterTestInProgress.value) return

        launch {
            isPrinterTestInProgress.value = true

            receiptPrinter.printTestReceipt()
                .onSuccess {
                    printerTestMessage.value = PrinterTestMessage(
                        id = nextPrinterMessageId(),
                        success = true,
                    )
                }
                .onFailure { error ->
                    refreshPrinterStatus()
                    printerTestMessage.value = PrinterTestMessage(
                        id = nextPrinterMessageId(),
                        success = false,
                        errorMessage = error.message,
                    )
                }

            isPrinterTestInProgress.value = false
        }
    }

    fun onPrinterTestMessageShown() {
        printerTestMessage.value = null
    }

    private fun nextPrinterMessageId(): Long {
        printerTestMessageId += 1
        return printerTestMessageId
    }
}
