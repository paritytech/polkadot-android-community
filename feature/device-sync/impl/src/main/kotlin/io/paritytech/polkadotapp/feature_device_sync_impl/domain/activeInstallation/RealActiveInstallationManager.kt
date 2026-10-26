package io.paritytech.polkadotapp.feature_device_sync_impl.domain.activeInstallation

import io.paritytech.polkadotapp.common.data.time.TimeProvider
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.common.utils.exponentialRetryDelay
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.retryUntilSuccess
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_coinage_api.data.installation.CoinageCurrentInstallationRepository
import io.paritytech.polkadotapp.feature_device_sync_api.domain.activeInstallation.ActiveInstallation
import io.paritytech.polkadotapp.feature_device_sync_api.domain.activeInstallation.ActiveInstallationManager
import io.paritytech.polkadotapp.feature_device_sync_api.domain.activeInstallation.InstallationStatus
import io.paritytech.polkadotapp.feature_device_sync_impl.data.activeInstallation.ActiveInstallationRecordRepository
import io.paritytech.polkadotapp.feature_device_sync_impl.data.activeInstallation.ActiveInstallationRecordsPage
import io.paritytech.polkadotapp.feature_device_sync_impl.data.device.DeviceInfoProvider
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.DeviceTransferKeySchedule
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.DeviceTransferKeyScheduleProvider
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Singleton
class RealActiveInstallationManager @Inject constructor(
    private val keyScheduleProvider: DeviceTransferKeyScheduleProvider,
    private val currentInstallationRepository: CoinageCurrentInstallationRepository,
    private val recordRepository: ActiveInstallationRecordRepository,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val timeProvider: TimeProvider,
    private val dispatchers: CoroutineDispatchers,
) : ActiveInstallationManager {
    private val mutableStatus = MutableStateFlow<InstallationStatus>(InstallationStatus.Unknown)
    override val status: StateFlow<InstallationStatus> = mutableStatus.asStateFlow()

    private val trackingContextMutex = Mutex()
    private var trackingContext: TrackingContext? = null

    private val recordsMutex = Mutex()
    private var latestRecord: ActiveInstallation? = null
    private var hasUnreadableRecord = false

    override suspend fun activateThisInstallation(): Result<Unit> = withContext(dispatchers.io) {
        getTrackingContext().flatMap { publishOwnRecord(it) }
    }

    suspend fun followActiveInstallation() {
        withContext(dispatchers.io) {
            val context = getTrackingContext()
                .logFailure("Failed to start following the active installation record")
                .getOrNull()
                ?: return@withContext

            retryUntilSuccess(FOLLOW_RETRY_DELAY) { runCancellableCatching { follow(context) } }
        }
    }

    private suspend fun follow(context: TrackingContext) = coroutineScope {
        var isHistoryLoaded = false

        recordRepository.subscribeRecords(context.keys).collect { pageResult ->
            pageResult
                .onSuccess { page ->
                    ingest(page)

                    if (!isHistoryLoaded && page.isComplete) {
                        isHistoryLoaded = true
                        if (isTopicEmpty()) launch { publishFirstInstallRecord(context) }
                    }

                    if (isHistoryLoaded) evaluate(context.ownInstallationId)
                }
                .onFailure { Timber.w(it, "Failed to read the active installation records") }
        }
    }

    private suspend fun publishFirstInstallRecord(context: TrackingContext) {
        for (attempt in 1..FIRST_INSTALL_ATTEMPTS) {
            if (!isTopicEmpty()) return

            publishOwnRecord(context)
                .onSuccess {
                    Timber.i("Published the active installation record: the topic had none")
                    return
                }
                .onFailure { Timber.w(it, "Failed to publish the active installation record, attempt $attempt") }

            delay(FIRST_INSTALL_RETRY_DELAY.delayFor(attempt))
        }
    }

    private suspend fun publishOwnRecord(context: TrackingContext): Result<Unit> {
        val deviceInfo = deviceInfoProvider.current()
        val installation = ActiveInstallation(
            installationId = context.ownInstallationId,
            deviceModel = deviceInfo.model,
            operatingSystem = deviceInfo.operatingSystem,
            activatedAt = Instant.fromEpochMilliseconds(timeProvider.now().toEpochMilliseconds()),
        )

        return recordRepository.publishRecord(installation, context.keys)
            .onSuccess {
                recordsMutex.withLock {
                    remember(installation)
                    updateStatus(context.ownInstallationId)
                }
            }
    }

    private suspend fun ingest(page: ActiveInstallationRecordsPage) = recordsMutex.withLock {
        page.records.forEach(::remember)
        if (page.hasUnreadableRecords) hasUnreadableRecord = true
    }

    private suspend fun isTopicEmpty(): Boolean = recordsMutex.withLock {
        latestRecord == null && !hasUnreadableRecord
    }

    private suspend fun evaluate(ownInstallationId: DataByteArray) = recordsMutex.withLock {
        updateStatus(ownInstallationId)
    }

    private fun remember(installation: ActiveInstallation) {
        latestRecord = latestRecord?.let { maxOf(it, installation, RECORD_ORDER) } ?: installation
    }

    private fun updateStatus(ownInstallationId: DataByteArray) {
        val latest = latestRecord
        val newStatus = when {
            latest == null -> if (hasUnreadableRecord) InstallationStatus.Unreadable else return
            latest.installationId == ownInstallationId -> InstallationStatus.Active
            else -> InstallationStatus.Inactive(latest)
        }

        if (mutableStatus.value != newStatus) {
            Timber.i("Active installation status: $newStatus")
            mutableStatus.value = newStatus
        }
    }

    private suspend fun getTrackingContext(): Result<TrackingContext> = trackingContextMutex.withLock {
        trackingContext?.let { return@withLock Result.success(it) }

        keyScheduleProvider.get()
            .flatMap { keys ->
                runCancellableCatching {
                    TrackingContext(keys, currentInstallationRepository.getOrCreateCurrent().value)
                }
            }
            .onSuccess { trackingContext = it }
    }

    private class TrackingContext(
        val keys: DeviceTransferKeySchedule,
        val ownInstallationId: DataByteArray,
    )

    private companion object {
        const val FIRST_INSTALL_ATTEMPTS = 10
        val FIRST_INSTALL_RETRY_DELAY = exponentialRetryDelay(initialDelay = 1.seconds, maxDelay = 30.seconds)
        val FOLLOW_RETRY_DELAY = exponentialRetryDelay(initialDelay = 5.seconds, maxDelay = 1.minutes)

        val RECORD_ORDER: Comparator<ActiveInstallation> = compareBy<ActiveInstallation> { it.activatedAt }
            .then(DataByteArray.compareByBytes<ActiveInstallation>(unsigned = true) { it.installationId.value })
            .thenBy { it.deviceModel }
            .thenBy { it.operatingSystem }
    }
}
