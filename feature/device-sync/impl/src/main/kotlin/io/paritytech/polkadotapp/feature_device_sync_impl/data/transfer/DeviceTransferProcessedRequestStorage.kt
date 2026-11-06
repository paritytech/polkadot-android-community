package io.paritytech.polkadotapp.feature_device_sync_impl.data.transfer

import io.paritytech.polkadotapp.common.data.storage.preferences.Preferences
import io.paritytech.polkadotapp.common.utils.mapToSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface DeviceTransferProcessedRequestStorage {
    fun observeProcessedRequestIds(): Flow<Set<UUID>>

    suspend fun markProcessed(requestId: UUID)
}

@Singleton
class RealDeviceTransferProcessedRequestStorage @Inject constructor(
    private val preferences: Preferences,
) : DeviceTransferProcessedRequestStorage {
    private val mutex = Mutex()

    override fun observeProcessedRequestIds(): Flow<Set<UUID>> {
        return preferences.stringSetFlow(KEY_PROCESSED_REQUEST_IDS).map { ids -> ids.mapToSet(UUID::fromString) }
    }

    override suspend fun markProcessed(requestId: UUID) = mutex.withLock {
        val processed = preferences.getStringSet(KEY_PROCESSED_REQUEST_IDS)
        preferences.putStringSet(KEY_PROCESSED_REQUEST_IDS, processed + requestId.toString())
    }

    private companion object {
        const val KEY_PROCESSED_REQUEST_IDS = "device_transfer_processed_request_ids"
    }
}
