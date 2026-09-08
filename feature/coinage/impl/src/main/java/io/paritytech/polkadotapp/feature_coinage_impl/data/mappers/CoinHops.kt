package io.paritytech.polkadotapp.feature_coinage_impl.data.mappers

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.paritytech.polkadotapp.common.utils.decodeFromByteArrayCatching
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Hop
import io.paritytech.polkadotapp.feature_coinage_impl.data.model.scale.CoinHopLocal
import io.paritytech.polkadotapp.feature_coinage_impl.data.model.scale.CoinHopsLocal
import kotlinx.serialization.encodeToByteArray

/**
 * Absent, empty and unreadable all read back as no hops.
 *
 * Swallowing a decode failure is deliberate here and nowhere else in this repository: hops drive only what a
 * details row draws, so a corrupt blob costs one row its circles, while propagating it would take down the
 * balance stream this mapper sits inside.
 */
fun ByteArray?.decodeCoinHops(): List<Hop> {
    if (this == null || isEmpty()) return emptyList()

    return BinaryScale.decodeFromByteArrayCatching<CoinHopsLocal>(this)
        .logFailure("Can't decode coin hops")
        .map { local -> local.hops.map { it.toDomain() } }
        .getOrDefault(emptyList())
}

fun List<Hop>.encodeCoinHops(): ByteArray =
    BinaryScale.encodeToByteArray(CoinHopsLocal(map { it.toLocal() }))

private fun CoinHopLocal.toDomain(): Hop = when (this) {
    is CoinHopLocal.Transfer -> Hop.Transfer(bundleSize)
    is CoinHopLocal.Split -> Hop.Split(fanout)
}

private fun Hop.toLocal(): CoinHopLocal = when (this) {
    is Hop.Transfer -> CoinHopLocal.Transfer(bundleSize)
    is Hop.Split -> CoinHopLocal.Split(fanout)
}
