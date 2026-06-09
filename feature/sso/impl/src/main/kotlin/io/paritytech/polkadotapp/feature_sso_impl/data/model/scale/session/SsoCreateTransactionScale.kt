package io.paritytech.polkadotapp.feature_sso_impl.data.model.scale.session

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.annotations.EnumIndex
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.annotations.FixedLength
import io.paritytech.polkadotapp.feature_products_api.model.scale.ProductAccountIdScale
import kotlinx.serialization.Serializable

@Serializable
class SsoEncodedTransactionExtensionValueScale(
    val id: String,
    val explicit: ByteArray,
    val implicit: ByteArray,
)

@Serializable
class SsoTxPayloadScale(
    val signer: ProductAccountIdScale,
    @FixedLength(32) val genesisHash: ByteArray,
    val callData: ByteArray,
    val extensions: List<SsoEncodedTransactionExtensionValueScale>,
    val txExtVersion: UByte,
)

@Serializable
sealed interface SsoVersionedTxPayloadScale {
    @Serializable
    @EnumIndex(0)
    class V1(val payload: SsoTxPayloadScale) : SsoVersionedTxPayloadScale
}

@Serializable
class SsoCreateTransactionRequestScale(val payload: SsoVersionedTxPayloadScale)
