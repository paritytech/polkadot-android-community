package io.paritytech.polkadotapp.feature_pgas_impl.data.extension

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.encodeToByteArray
import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.bandersnatch_crypto.intoBandersnatchContext
import io.paritytech.polkadotapp.common.utils.padEnd

internal fun BandersnatchContext.Companion.pgasClaim(period: UInt, slotIndex: UInt): BandersnatchContext {
    val contextBytes = "pop:gas:".encodeToByteArray() +
        BinaryScale.encodeToByteArray(period.toInt()) +
        BinaryScale.encodeToByteArray(slotIndex.toInt())

    return contextBytes.padEnd(32).intoBandersnatchContext()
}
