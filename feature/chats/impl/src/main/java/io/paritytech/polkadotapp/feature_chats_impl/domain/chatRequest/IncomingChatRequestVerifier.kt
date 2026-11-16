package io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest

import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.common.domain.model.X25519PublicKey
import io.paritytech.polkadotapp.common.domain.model.x25519OrNull
import io.paritytech.polkadotapp.feature_chain_resources_api.data.repository.ResourcesRepository
import io.paritytech.polkadotapp.feature_chain_resources_api.data.repository.requireConsumerInfo
import io.paritytech.polkadotapp.feature_chats_api.domain.model.IdentityProof
import io.paritytech.polkadotapp.feature_chats_impl.data.chatRequest.model.ChatRequestDecrypted
import io.paritytech.polkadotapp.feature_chats_impl.data.chatRequest.model.VersionedRequestContent
import io.paritytech.polkadotapp.feature_chats_impl.data.chatRequest.model.toDomain
import io.paritytech.polkadotapp.feature_chats_impl.data.chatRequest.signerAccountId
import javax.inject.Inject

interface IncomingChatRequestVerifier {
    suspend fun verify(request: ChatRequestDecrypted): Result<Unit>
}

class RealIncomingChatRequestVerifier @Inject constructor(
    private val identityProofCodec: IdentityProofCodec,
    private val resourcesRepository: ResourcesRepository,
    private val chainRegistry: ChainRegistry,
    private val knownChains: KnownChains,
) : IncomingChatRequestVerifier {
    override suspend fun verify(request: ChatRequestDecrypted): Result<Unit> = runCatching {
        val ok = when (val content = request.message.content) {
            is VersionedRequestContent.V1 -> return@runCatching

            is VersionedRequestContent.V2 -> {
                val proof = content.content.identityProof.toDomain()
                identityProofCodec.verify(
                    proof = proof,
                    statementAccountId = request.proof.signerAccountId(),
                    peerIdentityChatPubKey = peerChatPubKeyOf(proof),
                )
            }

            is VersionedRequestContent.Cancel -> {
                val proof = content.content.identityProof.toDomain()
                identityProofCodec.verifyCancel(
                    proof = proof,
                    requestId = request.message.messageId,
                    peerIdentityChatPubKey = peerChatPubKeyOf(proof),
                )
            }
        }
        if (!ok) error("IdentityProof verification failed for chat request ${request.message.messageId}")
    }

    private suspend fun peerChatPubKeyOf(proof: IdentityProof): X25519PublicKey {
        return resourcesRepository
            .requireConsumerInfo(chainRegistry.getChain(knownChains.people), proof.identityAccountId)
            .getOrThrow()
            .identifierKey
            .let { it.x25519OrNull() ?: error("Peer account uses an unsupported chat encryption key type") }
    }
}
