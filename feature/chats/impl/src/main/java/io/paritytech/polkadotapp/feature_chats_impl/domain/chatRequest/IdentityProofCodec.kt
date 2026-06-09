package io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.EncodedPublicKey
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.Secp256r1KeyGenerator
import io.paritytech.polkadotapp.common.utils.blake2b256
import io.paritytech.polkadotapp.common.utils.ecdhSharedSecret
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_account_api.domain.model.SharedSecretDerivationDomain
import io.paritytech.polkadotapp.feature_account_api.domain.usecase.SharedSecretDerivationUseCase
import io.paritytech.polkadotapp.feature_chats_api.domain.model.IdentityProof
import io.paritytech.polkadotapp.feature_chats_impl.data.chatRequest.model.IdentityProofPayloadScale
import kotlinx.serialization.encodeToByteArray
import javax.inject.Inject

class IdentityProofCodec @Inject constructor(
    private val accountRepository: AccountRepository,
    private val sharedSecretDerivationUseCase: SharedSecretDerivationUseCase,
    private val keyGenerator: Secp256r1KeyGenerator,
) {
    suspend fun produce(
        statementAccountId: AccountId,
        peerIdentityChatPubKey: EncodedPublicKey,
    ): IdentityProof {
        val identityAccountId = accountRepository.getWalletAccount().defaultAccountId()
        val sharedSecret = deriveSharedSecret(peerIdentityChatPubKey)
        val payload = encodePayload(identityAccountId, statementAccountId)

        return IdentityProof(
            identityAccountId = identityAccountId,
            proof = payload.blake2b256(key = sharedSecret).toDataByteArray(),
        )
    }

    suspend fun verify(
        proof: IdentityProof,
        statementAccountId: AccountId,
        peerIdentityChatPubKey: EncodedPublicKey,
    ): Boolean {
        val sharedSecret = deriveSharedSecret(peerIdentityChatPubKey)
        val payload = encodePayload(proof.identityAccountId, statementAccountId)
        val expected = payload.blake2b256(key = sharedSecret)

        return expected.contentEquals(proof.proof.value)
    }

    private suspend fun deriveSharedSecret(peerChatPubKey: EncodedPublicKey): ByteArray {
        val ourChatKeypair = sharedSecretDerivationUseCase.deriveForDomain(SharedSecretDerivationDomain.CHAT)
        val peerPublicKey = keyGenerator.derivePublicKey(peerChatPubKey.value)
        return ecdhSharedSecret(ourChatKeypair.private, peerPublicKey)
    }

    private fun encodePayload(identityAccountId: AccountId, statementAccountId: AccountId): ByteArray {
        return BinaryScale.encodeToByteArray(
            IdentityProofPayloadScale(
                identityAccountId = identityAccountId.value,
                statementAccountId = statementAccountId.value,
                context = CHAT_REQUEST_CONTEXT,
            )
        )
    }

    private companion object {
        const val CHAT_REQUEST_CONTEXT = "mds-chat-request"
    }
}
