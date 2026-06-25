package io.paritytech.polkadotapp.common.domain.printing

interface ReceiptPrinter {
    fun isAvailable(): Boolean

    suspend fun print(document: PrintDocument): Result<Unit>

    suspend fun printTestReceipt(): Result<Unit>
}
