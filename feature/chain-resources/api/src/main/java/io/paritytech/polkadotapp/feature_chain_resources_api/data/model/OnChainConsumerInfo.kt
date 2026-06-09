package io.paritytech.polkadotapp.feature_chain_resources_api.data.model

import androidx.annotation.Keep
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import kotlinx.serialization.Serializable

@Keep
@Serializable
class OnChainConsumerInfo(
    val identifierKey: DataByteArray,
    val fullUsername: String?,
    val liteUsername: String
)
