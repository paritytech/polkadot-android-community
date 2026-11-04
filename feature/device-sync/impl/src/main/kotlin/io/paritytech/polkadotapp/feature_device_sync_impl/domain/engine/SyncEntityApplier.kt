package io.paritytech.polkadotapp.feature_device_sync_impl.domain.engine

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.encodeToByteArray
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.AddContactUseCase
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.ApplyRemoteChatMessageUseCase
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.ApplySyncedChatStateUseCase
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.RemoveContactUseCase
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatStateForSync
import io.paritytech.polkadotapp.feature_chats_transport_protocol.scale.ChatMessageStatement
import io.paritytech.polkadotapp.feature_chats_transport_protocol.scale.IncomingStatusScale
import io.paritytech.polkadotapp.feature_chats_transport_protocol.scale.LocalStatusScale
import io.paritytech.polkadotapp.feature_chats_transport_protocol.scale.OutgoingStatusScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.ChatBlockStateScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.ChatIdScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.IncomingChatRequestScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.IncomingRequestStatusScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.IncomingRequestStatusUpdateScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.OutgoingChatRequestScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.OutgoingRequestStatusScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.OutgoingRequestStatusUpdateScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncEntityScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncUpdateScale
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject

/**
 * Applies a peer's inbound [SyncUpdateScale] to local state. Inbound `Devices` are ignored —
 * our own SSO sessions list is authoritative.
 */
class SyncEntityApplier @Inject constructor(
    private val addContactUseCase: AddContactUseCase,
    private val removeContactUseCase: RemoveContactUseCase,
    private val applyRemoteChatMessageUseCase: ApplyRemoteChatMessageUseCase,
    private val applySyncedChatStateUseCase: ApplySyncedChatStateUseCase,
) {
    suspend fun apply(update: SyncUpdateScale) {
        for (entity in update.entities) {
            runCatching {
                when (entity) {
                    is SyncEntityScale.Devices -> Unit
                    is SyncEntityScale.ChatsAdded -> applyChatsAdded(entity)
                    is SyncEntityScale.ChatsRemoved -> applyChatsRemoved(entity, removedAt = update.timePoint.toLong())
                    is SyncEntityScale.Messages -> applyMessages(entity)
                    is SyncEntityScale.ChatsBlocked -> applySyncedChatStateUseCase.applyBlockStates(entity.states.map { it.toBlockState() })
                    is SyncEntityScale.IncomingRequestsAdded ->
                        applySyncedChatStateUseCase.applyIncomingRequests(entity.requests.mapNotNull { it.toIncomingRequest() })
                    is SyncEntityScale.IncomingRequestsStatusUpdated ->
                        applySyncedChatStateUseCase.applyIncomingRequestUpdates(entity.updates.mapNotNull { it.toIncomingRequestUpdate() })
                    is SyncEntityScale.OutgoingRequestsAdded -> applyOutgoingRequestsAdded(entity)
                    is SyncEntityScale.OutgoingRequestsStatusUpdated -> applySyncedChatStateUseCase.applyOutgoingRequestUpdates(
                        updates = entity.updates.mapNotNull { it.toOutgoingRequestUpdate() },
                        removedAt = update.timePoint.toLong(),
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Timber.w(error, "SyncEntityApplier: failed to apply entity ${entity::class.simpleName} from update id=${update.id} — skipping")
            }
        }
    }

    private suspend fun applyChatsAdded(entity: SyncEntityScale.ChatsAdded) {
        val accountIds = entity.chats
            .filterIsInstance<ChatIdScale.Contact>()
            .map { AccountId(it.accountId) }
        addContactUseCase.addAlreadyEstablishedContactsById(accountIds)
            .onFailure { Timber.w(it, "SyncEntityApplier: failed to bootstrap ${accountIds.size} contacts") }
    }

    private suspend fun applyChatsRemoved(entity: SyncEntityScale.ChatsRemoved, removedAt: Long) {
        entity.chats.forEach { chat ->
            if (chat is ChatIdScale.Contact) {
                removeContactUseCase.recordRemoteContactRemoval(
                    accountId = AccountId(chat.accountId),
                    removedAt = removedAt,
                )
            }
        }
    }

    private suspend fun applyOutgoingRequestsAdded(entity: SyncEntityScale.OutgoingRequestsAdded) {
        applySyncedChatStateUseCase.applyPendingOutgoingRequests(entity.requests.mapNotNull { it.toPendingOutgoingRequest() })
            .onFailure { Timber.w(it, "SyncEntityApplier: failed to add contacts for ${entity.requests.size} outgoing requests") }
    }

    private suspend fun applyMessages(entity: SyncEntityScale.Messages) {
        Timber.d("Apply messages:\n%s", entity.messages.joinToString("\n") { it.logMessage() })
        entity.messages.forEach { message ->
            runCatching {
                val isOutgoing = message.status is LocalStatusScale.Outgoing
                val encoded = BinaryScale.encodeToByteArray<ChatMessageStatement>(message.remote)
                applyRemoteChatMessageUseCase.apply(
                    encoded = encoded,
                    peerAccountId = AccountId(message.peerId),
                    isOutgoing = isOutgoing,
                    status = message.status.toDomainStatus(),
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Timber.w(error, "SyncEntityApplier: failed to apply message id=${message.remote.id} — skipping")
            }
        }
    }

    private fun LocalStatusScale.toDomainStatus(): ChatMessage.Status = when (this) {
        is LocalStatusScale.Outgoing -> when (status) {
            OutgoingStatusScale.NEW -> ChatMessage.Status.PROCESSING
            OutgoingStatusScale.SENT -> ChatMessage.Status.IS_SENT
            OutgoingStatusScale.DELIVERED -> ChatMessage.Status.IS_READ
        }

        is LocalStatusScale.Incoming -> when (status) {
            IncomingStatusScale.NEW -> ChatMessage.Status.NEW
            IncomingStatusScale.SEEN -> ChatMessage.Status.IS_READ
        }
    }
}

private fun ChatBlockStateScale.toBlockState(): ChatStateForSync.BlockState = when (val chat = chatId) {
    is ChatIdScale.Contact -> ChatStateForSync.BlockState(AccountId(chat.accountId), isBlocked = blocked)
}

private fun IncomingChatRequestScale.toIncomingRequest(): ChatStateForSync.IncomingRequest? {
    val isAccepted = status.isAcceptedOrNull() ?: return null
    return ChatStateForSync.IncomingRequest(requestId, AccountId(peer), timestamp.toLong(), isAccepted)
}

private fun IncomingRequestStatusUpdateScale.toIncomingRequestUpdate(): ChatStateForSync.IncomingRequestUpdate? {
    val isAccepted = status.isAcceptedOrNull() ?: return null
    return ChatStateForSync.IncomingRequestUpdate(requestId, isAccepted)
}

private fun IncomingRequestStatusScale.isAcceptedOrNull(): Boolean? = when (this) {
    IncomingRequestStatusScale.PENDING -> null
    IncomingRequestStatusScale.ACCEPTED -> true
    IncomingRequestStatusScale.DECLINED -> false
}

private fun OutgoingChatRequestScale.toPendingOutgoingRequest(): ChatStateForSync.OutgoingRequest? {
    if (status != OutgoingRequestStatusScale.PENDING) return null
    return ChatStateForSync.OutgoingRequest(requestId, AccountId(peer), timestamp.toLong(), BinaryScale.encodeToByteArray(content))
}

private fun OutgoingRequestStatusUpdateScale.toOutgoingRequestUpdate(): ChatStateForSync.OutgoingRequestUpdate? = when (status) {
    OutgoingRequestStatusScale.PENDING -> null
    OutgoingRequestStatusScale.ACCEPTED -> ChatStateForSync.OutgoingRequestUpdate(requestId, isCancelled = false)
    OutgoingRequestStatusScale.CANCELLED -> ChatStateForSync.OutgoingRequestUpdate(requestId, isCancelled = true)
}
