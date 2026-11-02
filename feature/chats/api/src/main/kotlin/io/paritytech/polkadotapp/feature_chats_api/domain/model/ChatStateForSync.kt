package io.paritytech.polkadotapp.feature_chats_api.domain.model

import io.paritytech.polkadotapp.common.domain.model.AccountId

class ChatStateForSync(
    val blockStates: List<BlockState>,
    val processedIncomingRequests: List<IncomingRequest>,
    val pendingOutgoingRequests: List<OutgoingRequest>,
    val outgoingRequestUpdates: List<OutgoingRequestUpdate>,
) {
    class BlockState(val accountId: AccountId, val isBlocked: Boolean)

    class IncomingRequest(val id: ChatRequestId, val peer: AccountId, val timestamp: Long, val isAccepted: Boolean)

    class OutgoingRequest(val id: ChatRequestId, val peer: AccountId, val timestamp: Long, val encodedContent: ByteArray)

    class OutgoingRequestUpdate(val id: ChatRequestId, val isCancelled: Boolean)
}
