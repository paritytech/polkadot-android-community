package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.protocol

import io.paritytech.polkadotapp.common.data.time.TimeProvider
import io.paritytech.polkadotapp.common.utils.InformationSize.Companion.kilobytes
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.ApplyRemoteChatMessageUseCase
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageSyncCursor
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncEntityScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncUpdateScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.storage.SyncUpdateIdProvider
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.engine.ChatStateEntityCollector.Companion.NOTHING_SYNCED_CHECKPOINT
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.engine.SyncEntityChunker
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.engine.SyncEntityCollector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import kotlin.time.Instant

sealed interface DeviceTransferSenderStep {
    class Started(val totalChanges: Int) : DeviceTransferSenderStep

    class Update(val update: SyncUpdateScale) : DeviceTransferSenderStep
}

class DeviceTransferSenderSequencer @Inject constructor(
    private val collector: SyncEntityCollector,
    private val applyRemoteChatMessageUseCase: ApplyRemoteChatMessageUseCase,
    private val updateIdProvider: SyncUpdateIdProvider,
    private val timeProvider: TimeProvider,
) {
    private val chunker = SyncEntityChunker(UPDATE_ENTITY_BUDGET)

    fun stepsFrom(checkpoint: ULong?): Flow<DeviceTransferSenderStep> = flow {
        var round = Round(
            stateChangedAfter = NOTHING_SYNCED_CHECKPOINT,
            messagesUpdatedAfter = checkpoint?.let { Instant.fromEpochMilliseconds(it.toLong()) } ?: NOTHING_SYNCED_CHECKPOINT,
            messagesUpdatedUpTo = null,
            end = timeProvider.now(),
        )
        var chatState = collectChatState(round)
        var coinageState = collector.collectCoinageState(round.stateChangedAfter)
        val messageCount = applyRemoteChatMessageUseCase.countMessagesForSync(round.messagesUpdatedAfter, round.messagesUpdatedUpTo)
        emit(DeviceTransferSenderStep.Started(chatState.itemCount() + messageCount + coinageState.itemCount()))

        while (emitRound(round, chatState, coinageState) > 0) {
            val end = timeProvider.now()
            round = Round(stateChangedAfter = round.end, messagesUpdatedAfter = round.end, messagesUpdatedUpTo = end, end = end)
            chatState = collectChatState(round)
            coinageState = collector.collectCoinageState(round.stateChangedAfter)
        }
    }

    private suspend fun collectChatState(round: Round): List<SyncEntityScale> {
        return collector.collectChatState(round.stateChangedAfter).filterNot { it is SyncEntityScale.ChatsRemoved }
    }

    private suspend fun FlowCollector<DeviceTransferSenderStep>.emitRound(
        round: Round,
        chatState: List<SyncEntityScale>,
        coinageState: List<SyncEntityScale>,
    ): Int {
        var itemCount = emitChanges(round, chatState)
        var cursor: ChatMessageSyncCursor? = null

        do {
            val page = applyRemoteChatMessageUseCase.getMessagePageForSync(
                updatedAfter = round.messagesUpdatedAfter,
                updatedUpTo = round.messagesUpdatedUpTo,
                after = cursor,
                limit = MESSAGE_PAGE_SIZE,
            )
            itemCount += emitChanges(round, listOfNotNull(collector.toMessagesEntity(page.messages)))
            cursor = page.nextCursor
        } while (cursor != null)

        itemCount += emitChanges(round, coinageState)
        emit(DeviceTransferSenderStep.Update(SyncUpdateScale(updateIdProvider.nextId(), emptyList(), round.end.toTimePoint())))
        return itemCount
    }

    private suspend fun FlowCollector<DeviceTransferSenderStep>.emitChanges(round: Round, entities: List<SyncEntityScale>): Int {
        chunker.chunk(entities).forEach { chunk ->
            val update = SyncUpdateScale(updateIdProvider.nextId(), chunk, round.messagesUpdatedAfter.toTimePoint())
            emit(DeviceTransferSenderStep.Update(update))
        }
        return entities.itemCount()
    }

    private fun Instant.toTimePoint(): ULong = toEpochMilliseconds().toULong()

    private class Round(
        val stateChangedAfter: Instant,
        val messagesUpdatedAfter: Instant,
        val messagesUpdatedUpTo: Instant?,
        val end: Instant,
    )

    private companion object {
        const val MESSAGE_PAGE_SIZE = 200
        val UPDATE_ENTITY_BUDGET = 60.kilobytes
    }
}
