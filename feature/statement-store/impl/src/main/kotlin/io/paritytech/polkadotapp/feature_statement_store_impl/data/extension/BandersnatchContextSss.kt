package io.paritytech.polkadotapp.feature_statement_store_impl.data.extension

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.bandersnatch_crypto.intoBandersnatchContext
import io.paritytech.polkadotapp.common.utils.padEnd
import io.paritytech.polkadotapp.common.utils.toBigEndianByteArray

internal fun BandersnatchContext.Companion.statementStoreSlot(period: UInt, seq: UInt): BandersnatchContext {
    val contextBytes = "SSS_SLOT:".encodeToByteArray() +
        period.toInt().toBigEndianByteArray() +
        seq.toInt().toBigEndianByteArray()

    return contextBytes.padEnd(32, padding = 0x20).intoBandersnatchContext()
}
