package io.paritytech.polkadotapp.feature_statement_store_api.data

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.EncodedPublicKey
import io.paritytech.polkadotapp.feature_account_api.domain.model.SharedSecretDerivationDomain
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.EncodedMessage

interface StatementRequestDecoder {
    suspend fun decodeMessages(
        sharedSecretDerivationDomain: SharedSecretDerivationDomain,
        peerEncryptionPublicKey: EncodedPublicKey,
        ourStatementAccountId: AccountId,
        encryptedRequest: ByteArray,
    ): Result<List<EncodedMessage>>
}
