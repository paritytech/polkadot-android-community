package io.paritytech.polkadotapp.feature_chain_resources_api.domain.model

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.EncodedPublicKey

class ConsumerInfo(
    val accountId: AccountId,
    val identifierKey: EncodedPublicKey,
    val fullUsername: String?,
    val liteUsername: String
) {
    val username: String
        get() = fullUsername ?: liteUsername
}
