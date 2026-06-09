package io.paritytech.polkadotapp.feature_sso_impl.data.model.scale.session

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.annotations.EnumIndex
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.types.BSResult
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.feature_products_api.model.scale.ProductAccountIdScale
import io.paritytech.polkadotapp.feature_products_api.model.scale.ProductIdScale
import io.paritytech.polkadotapp.feature_sso_impl.domain.session.model.SsoSessionRequestId
import kotlinx.serialization.Serializable

/**
 * Union of SSO message types that can be exchanged between wallet and host.
 */
@Serializable
sealed class SsoMessageContent {
    @Serializable
    @EnumIndex(0)
    data object Disconnected : SsoMessageContent()

    @Serializable
    @EnumIndex(1)
    class SigningRequest(val request: SsoSigningRequestScale) : SsoMessageContent()

    @Serializable
    @EnumIndex(2)
    class SigningResponse(
        val respondingTo: SsoSessionRequestId,
        val signedPayload: BSResult<SsoSignedPayloadJsonScale, String>
    ) : SsoMessageContent()

    @Serializable
    @EnumIndex(3)
    class RingVrfAliasRequest(
        val requestedAccount: ProductAccountIdScale,
        val callingProductId: ProductIdScale,
    ) : SsoMessageContent()

    @Serializable
    @EnumIndex(4)
    class RingVrfAliasResponse(
        val respondingTo: SsoSessionRequestId,
        val payload: BSResult<SsoContextualAliasScale, String>,
    ) : SsoMessageContent()

    @Serializable
    @EnumIndex(5)
    class ResourceAllocationRequest(
        val request: SsoResourceAllocationRequestScale,
    ) : SsoMessageContent()

    @Serializable
    @EnumIndex(6)
    class ResourceAllocationResponse(
        val respondingTo: SsoSessionRequestId,
        val payload: BSResult<List<SsoApAllocationOutcomeScale>, String>,
    ) : SsoMessageContent()

    @Serializable
    @EnumIndex(7)
    class CreateTransactionRequest(val request: SsoCreateTransactionRequestScale) : SsoMessageContent()

    @Serializable
    @EnumIndex(8)
    class CreateTransactionResponse(
        val respondingTo: SsoSessionRequestId,
        val signedTx: BSResult<DataByteArray, String>,
    ) : SsoMessageContent()
}
