package io.paritytech.polkadotapp.common.domain.printing

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import dagger.hilt.android.qualifiers.ApplicationContext
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import woyou.aidlservice.jiuiv5.ICallback
import woyou.aidlservice.jiuiv5.IWoyouService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.max

class SunmiReceiptPrinter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: CoroutineDispatchers,
) : ReceiptPrinter {

    override fun isAvailable(): Boolean {
        @Suppress("DEPRECATION")
        val isPackageInstalled = runCatching {
            context.packageManager.getPackageInfo(SUNMI_SERVICE_PACKAGE, 0)
        }.isSuccess

        val intent = Intent(SUNMI_SERVICE_ACTION).setPackage(SUNMI_SERVICE_PACKAGE)
        val isServiceResolvable = context.packageManager.resolveService(intent, 0) != null

        return isPackageInstalled || isServiceResolvable
    }

    override suspend fun print(document: PrintDocument): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            check(isAvailable()) { "PrinterUnavailable" }
            withPrinter { printer ->
                ensurePrinterReady(printer)
                renderDocument(printer, document)
                delay(500)
            }
        }
    }

    override suspend fun printTestReceipt(): Result<Unit> {
        val document = PrintDocument(
            kind = PrintDocumentKind.CustomerReceipt,
            paperWidth = PrinterPaperWidth.Mm58,
            title = "POLKADOT APP",
            subtitle = "Sunmi printer test",
            body = listOf(
                PrintLine("Printed", timestamp()),
                PrintLine("Customer receipt", "OK"),
                PrintLine("X report", "OK"),
                PrintLine("Z report", "OK"),
                PrintLine("Device", "${Build.MANUFACTURER} ${Build.MODEL}"),
            ),
            qr = PrintQr("polkadotapp://printer-test", moduleSize = DEFAULT_QR_MODULE_SIZE),
            footer = listOf("Host printing ready"),
        )

        return print(document)
    }

    private fun ensurePrinterReady(printer: IWoyouService) {
        val state = printer.updatePrinterState()
        check(state == PRINTER_STATE_READY) {
            printerStateMessage(state)
        }
    }

    private suspend fun renderDocument(printer: IWoyouService, document: PrintDocument) {
        val columns = document.paperWidth.columns

        printer.printerInit(LoggingPrinterCallback)
        printer.setAlignment(ALIGN_CENTER, LoggingPrinterCallback)
        printer.setFontSize(28f, LoggingPrinterCallback)
        printer.printText("${document.title}\n", LoggingPrinterCallback)
        document.subtitle?.takeIf(String::isNotBlank)?.let {
            printer.setFontSize(22f, LoggingPrinterCallback)
            printer.printText("$it\n", LoggingPrinterCallback)
        }
        printer.setFontSize(20f, LoggingPrinterCallback)

        printer.setAlignment(ALIGN_LEFT, LoggingPrinterCallback)
        printLines(printer, document.header, columns)
        if (document.isReport()) {
            printSectionHeader(printer, "PERIOD", columns)
            printLines(printer, document.body, columns)
            if (document.items.isNotEmpty()) {
                printSectionHeader(printer, "ITEM SUMMARY", columns)
                printItems(printer, document.items, columns)
            }
            if (document.totals.isNotEmpty()) {
                printSectionHeader(printer, "TOTALS", columns)
                printLines(printer, document.totals, columns)
            }
        } else {
            printDivider(printer, columns)
            printLines(printer, document.body, columns)
            if (document.items.isNotEmpty()) {
                printer.printText("\n", LoggingPrinterCallback)
                printItems(printer, document.items, columns)
            }
            if (document.totals.isNotEmpty()) {
                printDivider(printer, columns)
                printLines(printer, document.totals, columns)
            }
        }

        document.qr?.let { qr ->
            qr.label?.takeIf(String::isNotBlank)?.let {
                printer.setAlignment(ALIGN_CENTER, LoggingPrinterCallback)
                printer.printText("$it\n", LoggingPrinterCallback)
            }
            printer.setAlignment(ALIGN_CENTER, LoggingPrinterCallback)
            val moduleSize = qr.moduleSize?.coerceIn(MIN_QR_MODULE_SIZE, MAX_QR_MODULE_SIZE) ?: DEFAULT_QR_MODULE_SIZE
            printer.printQRCode(qr.data, moduleSize, QR_ERROR_CORRECTION, LoggingPrinterCallback)
            printer.setAlignment(ALIGN_LEFT, LoggingPrinterCallback)
        }

        val footerLines = document.footer.filter(String::isNotBlank)
        if (footerLines.isNotEmpty()) {
            printDivider(printer, columns)
        }
        footerLines.forEach {
            printer.setAlignment(ALIGN_CENTER, LoggingPrinterCallback)
            printer.printText("${it.take(columns)}\n", LoggingPrinterCallback)
        }

        printer.setAlignment(ALIGN_LEFT, LoggingPrinterCallback)
        printer.lineWrap(4, LoggingPrinterCallback)
    }

    private fun printLines(printer: IWoyouService, lines: List<PrintLine>, columns: Int) {
        lines.forEach { line ->
            val value = line.value
            if (value.isNullOrBlank()) {
                printWrapped(printer, line.label, columns)
            } else {
                printer.printText("${twoColumn(line.label, value, columns)}\n", LoggingPrinterCallback)
            }
        }
    }

    private fun printItems(printer: IWoyouService, items: List<PrintItem>, columns: Int) {
        if (items.isEmpty()) return

        val quantityWidth = if (columns <= PrinterPaperWidth.Mm58.columns) 4 else 5
        val totalWidth = if (columns <= PrinterPaperWidth.Mm58.columns) 8 else 10
        val nameWidth = columns - quantityWidth - totalWidth
        printer.printColumnsText(
            arrayOf("Item", "Qty", "Total"),
            intArrayOf(nameWidth, quantityWidth, totalWidth),
            intArrayOf(ALIGN_LEFT, ALIGN_CENTER, ALIGN_RIGHT),
            LoggingPrinterCallback
        )

        items.forEach { item ->
            val quantity = item.quantity.orEmpty()
            val total = item.total.orEmpty()
            val nameLines = wrap(item.name, max(8, nameWidth))
            printer.printColumnsText(
                arrayOf(nameLines.firstOrNull().orEmpty(), quantity.take(quantityWidth), total.take(totalWidth)),
                intArrayOf(nameWidth, quantityWidth, totalWidth),
                intArrayOf(ALIGN_LEFT, ALIGN_CENTER, ALIGN_RIGHT),
                LoggingPrinterCallback
            )
            nameLines.drop(1).forEach {
                printer.printColumnsText(
                    arrayOf(it.take(nameWidth), "", ""),
                    intArrayOf(nameWidth, quantityWidth, totalWidth),
                    intArrayOf(ALIGN_LEFT, ALIGN_CENTER, ALIGN_RIGHT),
                    LoggingPrinterCallback
                )
            }
        }
    }

    private fun printDivider(printer: IWoyouService, columns: Int) {
        printer.printText("${"-".repeat(columns)}\n", LoggingPrinterCallback)
    }

    private fun printSectionHeader(printer: IWoyouService, label: String, columns: Int) {
        printer.printText("\n", LoggingPrinterCallback)
        printDivider(printer, columns)
        printer.setAlignment(ALIGN_CENTER, LoggingPrinterCallback)
        printer.printText("${label.take(columns)}\n", LoggingPrinterCallback)
        printer.setAlignment(ALIGN_LEFT, LoggingPrinterCallback)
        printDivider(printer, columns)
    }

    private fun printWrapped(printer: IWoyouService, text: String, columns: Int) {
        wrap(text, columns).forEach {
            printer.printText("${it}\n", LoggingPrinterCallback)
        }
    }

    private fun twoColumn(label: String, value: String, columns: Int): String {
        val minGap = 1
        val right = value.take(columns)
        val leftWidth = columns - right.length - minGap
        if (leftWidth <= 0) return right

        val left = label.take(leftWidth)
        return left + " ".repeat(columns - left.length - right.length) + right
    }

    private fun wrap(text: String, columns: Int): List<String> {
        if (text.length <= columns) return listOf(text)

        val lines = mutableListOf<String>()
        var remaining = text
        while (remaining.length > columns) {
            val splitAt = remaining.take(columns + 1).lastIndexOf(' ').takeIf { it > 0 } ?: columns
            lines += remaining.take(splitAt).trimEnd()
            remaining = remaining.drop(splitAt).trimStart()
        }
        if (remaining.isNotBlank()) lines += remaining
        return lines
    }

    private fun PrintDocument.isReport(): Boolean {
        return kind == PrintDocumentKind.XReport || kind == PrintDocumentKind.ZReport
    }

    private suspend fun withPrinter(block: suspend (IWoyouService) -> Unit) {
        val connection = SunmiPrinterConnection()
        val bound = context.bindService(
            Intent(SUNMI_SERVICE_ACTION).setPackage(SUNMI_SERVICE_PACKAGE),
            connection,
            Context.BIND_AUTO_CREATE
        )
        check(bound) { "Sunmi printer service is not available" }

        try {
            val printer = withTimeout(BIND_TIMEOUT_MS) { connection.awaitService() }
            block(printer)
        } finally {
            runCatching { context.unbindService(connection) }
        }
    }

    private class SunmiPrinterConnection : ServiceConnection {
        private val service = CompletableDeferred<IWoyouService>()

        suspend fun awaitService(): IWoyouService = service.await()

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val printer = IWoyouService.Stub.asInterface(binder)
            if (printer == null) {
                service.completeExceptionally(IllegalStateException("Sunmi printer binder is unavailable"))
            } else {
                service.complete(printer)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) = Unit
    }

    private object LoggingPrinterCallback : ICallback.Stub() {
        override fun onRunResult(isSuccess: Boolean) {
            if (!isSuccess) Timber.w("Sunmi printer call reported failure")
        }

        override fun onReturnString(result: String?) {
            Timber.d("Sunmi printer callback: $result")
        }

        override fun onRaiseException(code: Int, msg: String?) {
            Timber.w("Sunmi printer exception $code: $msg")
        }

        override fun onPrintResult(code: Int, msg: String?) {
            if (code != 0) Timber.w("Sunmi printer result $code: $msg")
        }
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    }

    private fun printerStateMessage(state: Int): String = when (state) {
        PRINTER_STATE_PREPARING -> "Printer is preparing"
        PRINTER_STATE_COMMUNICATION_ERROR -> "Printer communication error"
        PRINTER_STATE_OUT_OF_PAPER -> "Printer is out of paper"
        PRINTER_STATE_OVERHEATED -> "Printer is overheated"
        PRINTER_STATE_MISSING -> "PrinterUnavailable"
        PRINTER_STATE_UPDATE_FAILED -> "Printer state update failed"
        else -> "Printer is not ready ($state)"
    }

    private companion object {
        const val SUNMI_SERVICE_ACTION = "woyou.aidlservice.jiuiv5.IWoyouService"
        const val SUNMI_SERVICE_PACKAGE = "woyou.aidlservice.jiuiv5"
        const val BIND_TIMEOUT_MS = 5_000L
        const val DEFAULT_QR_MODULE_SIZE = 6
        const val MIN_QR_MODULE_SIZE = 1
        const val MAX_QR_MODULE_SIZE = 16
        const val PRINTER_STATE_READY = 1
        const val PRINTER_STATE_PREPARING = 2
        const val PRINTER_STATE_COMMUNICATION_ERROR = 3
        const val PRINTER_STATE_OUT_OF_PAPER = 4
        const val PRINTER_STATE_OVERHEATED = 5
        const val PRINTER_STATE_MISSING = 505
        const val PRINTER_STATE_UPDATE_FAILED = 507
        const val QR_ERROR_CORRECTION = 1

        const val ALIGN_LEFT = 0
        const val ALIGN_CENTER = 1
        const val ALIGN_RIGHT = 2
    }
}
