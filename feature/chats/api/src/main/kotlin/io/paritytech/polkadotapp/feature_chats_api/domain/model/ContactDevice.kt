package io.paritytech.polkadotapp.feature_chats_api.domain.model

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.EncodedPublicKey

typealias ContactAccountId = AccountId

data class ContactDevice(
    val contactAccountId: ContactAccountId,
    val statementAccountId: AccountId,
    val encryptionPublicKey: EncodedPublicKey,
)
