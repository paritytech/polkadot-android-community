package io.paritytech.polkadotapp.feature_coinage_impl.domain.externalPayment

import com.google.gson.Gson
import io.novasama.substrate_sdk_android.extensions.fromHex
import io.novasama.substrate_sdk_android.extensions.toHexString
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.decodeFromByteArray
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageInstallationId
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageKeyIndex
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToByteArray
import javax.inject.Inject

class SelectedVoucherKeysCodec @Inject constructor() {
    fun encode(keys: List<CoinageKeyIndex>): String {
        val scale = keys.map { SelectedVoucherKeyScale(it.installation.value, it.item) }
        return BinaryScale.encodeToByteArray(scale).toHexString(withPrefix = true)
    }

    // Rows written before installations existed hold a JSON array of bare ring indices, all under the legacy page.
    fun decode(stored: String): List<CoinageKeyIndex> {
        if (stored.startsWith(LEGACY_ARRAY_START)) {
            return Gson().fromJson(stored, IntArray::class.java).map { CoinageKeyIndex(CoinageInstallationId.LEGACY_ZERO, it) }
        }

        return BinaryScale.decodeFromByteArray<List<SelectedVoucherKeyScale>>(stored.fromHex())
            .map { CoinageKeyIndex(CoinageInstallationId(it.installation), it.item) }
    }

    private companion object {
        const val LEGACY_ARRAY_START = "["
    }
}

@Serializable
private class SelectedVoucherKeyScale(
    val installation: DataByteArray,
    val item: Int,
)
