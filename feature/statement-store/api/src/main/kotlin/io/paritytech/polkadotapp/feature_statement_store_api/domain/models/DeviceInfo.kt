package io.paritytech.polkadotapp.feature_statement_store_api.domain.models

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.EncodedPublicKey

data class DeviceInfo(
    val statementAccountId: AccountId,
    val encryptionPublicKey: EncodedPublicKey,
)
