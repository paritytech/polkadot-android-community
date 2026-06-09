package io.paritytech.polkadotapp.feature_transaction_storage_impl.data.extension

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.bandersnatch_crypto.intoBandersnatchContext
import io.paritytech.polkadotapp.common.utils.padEnd
import io.paritytech.polkadotapp.common.utils.toBigEndianByteArray

internal fun BandersnatchContext.Companion.longTermStorageClaim(period: UInt, counter: UByte): BandersnatchContext {
    val contextBytes = "pop:polkadot.net/rsc-lts".encodeToByteArray() +
        period.toInt().toBigEndianByteArray() +
        byteArrayOf(counter.toByte())

    return contextBytes.padEnd(32).intoBandersnatchContext()
}
