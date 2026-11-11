package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.protocol

import io.paritytech.polkadotapp.common.data.time.TimeProvider
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.utils.InformationSize.Companion.kilobytes
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncEntityScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncUpdateScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.storage.SyncUpdateIdProvider
import io.paritytech.polkadotapp.feature_device_sync_impl.data.transfer.DeviceTransferCheckpointStorage
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.engine.ChatStateEntityCollector.Companion.NOTHING_SYNCED_CHECKPOINT
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.engine.SyncEntityApplier
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.engine.SyncEntityChunker
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.engine.SyncEntityCollector
import io.paritytech.polkadotapp.feature_statement_store_api.domain.OurDeviceKeypairProvider
import javax.inject.Inject
import kotlin.time.Instant

class DeviceTransferProtocolFactory @Inject constructor(
    private val collector: SyncEntityCollector,
    private val applier: SyncEntityApplier,
    private val ourDeviceKeypairProvider: OurDeviceKeypairProvider,
    private val checkpointStorage: DeviceTransferCheckpointStorage,
    private val updateIdProvider: SyncUpdateIdProvider,
    private val timeProvider: TimeProvider,
) {
    private val chunker = SyncEntityChunker(UPDATE_ENTITY_BUDGET)

    fun createSender(): DeviceTransferSender = DeviceTransferSender(::collectUpdates, ourDeviceKeypairProvider)

    fun createReceiver(senderInstallationId: DataByteArray): DeviceTransferReceiver {
        return DeviceTransferReceiver(senderInstallationId, applier, checkpointStorage, ourDeviceKeypairProvider)
    }

    private suspend fun collectUpdates(checkpoint: ULong?): List<SyncUpdateScale> {
        val roundEnd = timeProvider.now().toEpochMilliseconds().toULong()
        val messagesCheckpoint = checkpoint?.let { Instant.fromEpochMilliseconds(it.toLong()) } ?: NOTHING_SYNCED_CHECKPOINT
        val entities = collector.collectChatState(NOTHING_SYNCED_CHECKPOINT).filterNot { it is SyncEntityScale.ChatsRemoved } +
            listOfNotNull(collector.collectMessages(messagesCheckpoint))

        val changes = chunker.chunk(entities).map { chunk ->
            SyncUpdateScale(id = updateIdProvider.nextId(), entities = chunk, timePoint = checkpoint ?: 0uL)
        }
        val roundClosing = SyncUpdateScale(id = updateIdProvider.nextId(), entities = emptyList(), timePoint = roundEnd)

        return changes + roundClosing
    }

    private companion object {
        val UPDATE_ENTITY_BUDGET = 60.kilobytes
    }
}
