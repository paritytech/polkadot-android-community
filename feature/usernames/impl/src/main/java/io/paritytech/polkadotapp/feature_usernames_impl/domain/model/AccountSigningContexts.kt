package io.paritytech.polkadotapp.feature_usernames_impl.domain.model

import io.paritytech.polkadotapp.common.domain.model.DataByteArray

class AccountSigningContexts(
    val peopleLite: DataByteArray?,
    val dotNsGateway: DataByteArray?
)
