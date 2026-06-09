package io.paritytech.polkadotapp.feature_coinage_impl.domain.model

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.annotations.AsTuple
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import kotlinx.serialization.Serializable

@AsTuple
@Serializable
data class SplitDestination(
    val exponent: ValueExponent,
    val accountIds: List<AccountId>
)
