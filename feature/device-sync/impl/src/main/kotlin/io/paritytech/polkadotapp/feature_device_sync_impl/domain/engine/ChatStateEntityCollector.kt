package io.paritytech.polkadotapp.feature_device_sync_impl.domain.engine

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.paritytech.polkadotapp.common.utils.decodeFromByteArrayCatching
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.ChatStateSyncUseCase
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatStateForSync
import io.paritytech.polkadotapp.feature_chats_transport_protocol.scale.VersionedRequestContent
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.ChatBlockStateScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.ChatIdScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.IncomingChatRequestScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.IncomingRequestStatusScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.OutgoingChatRequestScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.OutgoingRequestStatusScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.OutgoingRequestStatusUpdateScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncEntityScale
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Instant

class ChatStateEntityCollector @Inject constructor(
    private val chatStateSyncUseCase: ChatStateSyncUseCase,
) {
    suspend fun collect(checkpoint: Instant): List<SyncEntityScale> {
        val state = chatStateSyncUseCase.getChatState(changedAfter = checkpoint.takeIf { it != NOTHING_SYNCED_CHECKPOINT })

        return listOfNotNull(
            state.pendingOutgoingRequests.mapNotNull { it.toScaleOrNull() }
                .takeIf { it.isNotEmpty() }?.let(SyncEntityScale::OutgoingRequestsAdded),
            state.blockStates.map { it.toScale() }
                .takeIf { it.isNotEmpty() }?.let(SyncEntityScale::ChatsBlocked),
            state.processedIncomingRequests.map { it.toScale() }
                .takeIf { it.isNotEmpty() }?.let(SyncEntityScale::IncomingRequestsAdded),
            state.outgoingRequestUpdates.map { it.toScale() }
                .takeIf { it.isNotEmpty() }?.let(SyncEntityScale::OutgoingRequestsStatusUpdated),
        )
    }

    private fun ChatStateForSync.OutgoingRequest.toScaleOrNull(): OutgoingChatRequestScale? {
        return BinaryScale.decodeFromByteArrayCatching<VersionedRequestContent>(encodedContent)
            .onFailure { Timber.w(it, "ChatStateEntityCollector: failed to decode content of outgoing request $id") }
            .getOrNull()
            ?.let { content ->
                OutgoingChatRequestScale(
                    requestId = id,
                    peer = peer.value,
                    timestamp = timestamp.toULong(),
                    status = OutgoingRequestStatusScale.PENDING,
                    content = content,
                )
            }
    }

    private fun ChatStateForSync.BlockState.toScale(): ChatBlockStateScale {
        return ChatBlockStateScale(chatId = ChatIdScale.Contact(accountId.value), blocked = isBlocked)
    }

    private fun ChatStateForSync.IncomingRequest.toScale(): IncomingChatRequestScale {
        return IncomingChatRequestScale(
            requestId = id,
            peer = peer.value,
            timestamp = timestamp.toULong(),
            status = if (isAccepted) IncomingRequestStatusScale.ACCEPTED else IncomingRequestStatusScale.DECLINED,
            peerDevice = null,
            pushToken = null,
            welcomeMessage = null,
        )
    }

    private fun ChatStateForSync.OutgoingRequestUpdate.toScale(): OutgoingRequestStatusUpdateScale {
        return OutgoingRequestStatusUpdateScale(
            requestId = id,
            status = if (isCancelled) OutgoingRequestStatusScale.CANCELLED else OutgoingRequestStatusScale.ACCEPTED,
        )
    }

    private companion object {
        val NOTHING_SYNCED_CHECKPOINT = Instant.fromEpochMilliseconds(0)
    }
}
