package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.protocol

import io.paritytech.polkadotapp.common.data.time.TimeProvider
import io.paritytech.polkadotapp.common.utils.InformationSize.Companion.kilobytes
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncEntityScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncUpdateScale
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
    private val timeProvider: TimeProvider,
) {
    private val chunker = SyncEntityChunker(UPDATE_ENTITY_BUDGET)

    suspend fun createSender(): Result<DeviceTransferSender> = runCancellableCatching {
        val snapshot = collector.collect(Instant.fromEpochMilliseconds(0))
            .filter { it is SyncEntityScale.ChatsAdded || it is SyncEntityScale.Messages }
        val timePoint = timeProvider.now().toEpochMilliseconds().toULong()
        val updates = chunker.chunk(snapshot).mapIndexed { index, entities ->
            SyncUpdateScale(id = (index + 1).toUInt(), entities = entities, timePoint = timePoint)
        }

        DeviceTransferSender(updates, ourDeviceKeypairProvider.get().privateKey.bytes)
    }

    fun createReceiver(): DeviceTransferReceiver = DeviceTransferReceiver(applier::apply)

    private companion object {
        val UPDATE_ENTITY_BUDGET = 60.kilobytes
    }
}
