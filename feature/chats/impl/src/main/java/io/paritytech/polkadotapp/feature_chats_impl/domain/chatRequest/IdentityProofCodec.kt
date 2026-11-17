package io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.X25519PublicKey
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.blake2b256
import io.paritytech.polkadotapp.common.utils.x25519SharedSecret
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_account_api.domain.model.SharedSecretDerivationDomain
import io.paritytech.polkadotapp.feature_account_api.domain.usecase.SharedSecretDerivationUseCase
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatRequestId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.IdentityProof
import io.paritytech.polkadotapp.feature_chats_impl.data.chatRequest.model.CancelProofPayloadScale
import io.paritytech.polkadotapp.feature_chats_impl.data.chatRequest.model.IdentityProofPayloadScale
import kotlinx.serialization.encodeToByteArray
import javax.inject.Inject

class IdentityProofCodec @Inject constructor(
    private val accountRepository: AccountRepository,
    private val sharedSecretDerivationUseCase: SharedSecretDerivationUseCase,
) {
    suspend fun produce(
        statementAccountId: AccountId,
        peerIdentityChatPubKey: X25519PublicKey,
    ): IdentityProof {
        val identityAccountId = accountRepository.getWalletAccount().defaultAccountId()
        val payload = encodePayload(identityAccountId, statementAccountId)

        return IdentityProof(
            identityAccountId = identityAccountId,
            proof = keyedHash(payload, peerIdentityChatPubKey).toDataByteArray(),
        )
    }

    suspend fun verify(
        proof: IdentityProof,
        statementAccountId: AccountId,
        peerIdentityChatPubKey: X25519PublicKey,
    ): Boolean {
        val payload = encodePayload(proof.identityAccountId, statementAccountId)

        return keyedHash(payload, peerIdentityChatPubKey).contentEquals(proof.proof.value)
    }

    suspend fun produceCancel(
        requestId: ChatRequestId,
        peerIdentityChatPubKey: X25519PublicKey,
    ): IdentityProof {
        val identityAccountId = accountRepository.getWalletAccount().defaultAccountId()
        val payload = encodeCancelPayload(identityAccountId, requestId)

        return IdentityProof(
            identityAccountId = identityAccountId,
            proof = keyedHash(payload, peerIdentityChatPubKey).toDataByteArray(),
        )
    }

    suspend fun verifyCancel(
        proof: IdentityProof,
        requestId: ChatRequestId,
        peerIdentityChatPubKey: X25519PublicKey,
    ): Boolean {
        val payload = encodeCancelPayload(proof.identityAccountId, requestId)

        return keyedHash(payload, peerIdentityChatPubKey).contentEquals(proof.proof.value)
    }

    private suspend fun keyedHash(payload: ByteArray, peerChatPubKey: X25519PublicKey): ByteArray {
        val ourChatKeypair = sharedSecretDerivationUseCase.deriveForDomain(SharedSecretDerivationDomain.CHAT)
        val sharedSecret = x25519SharedSecret(ourChatKeypair.privateKey, peerChatPubKey).getOrThrow().bytes.value

        return payload.blake2b256(key = sharedSecret)
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

    private fun encodeCancelPayload(identityAccountId: AccountId, requestId: ChatRequestId): ByteArray {
        return BinaryScale.encodeToByteArray(
            CancelProofPayloadScale(
                identityAccountId = identityAccountId.value,
                requestId = requestId,
                context = CHAT_REQUEST_CANCEL_CONTEXT,
            )
        )
    }

    private companion object {
        const val CHAT_REQUEST_CONTEXT = "mds-chat-request"
        const val CHAT_REQUEST_CANCEL_CONTEXT = "mds-chat-request-cancel"
    }
}
