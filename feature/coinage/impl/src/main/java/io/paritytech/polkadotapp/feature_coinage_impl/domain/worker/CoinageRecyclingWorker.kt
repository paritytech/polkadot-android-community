package io.paritytech.polkadotapp.feature_coinage_impl.domain.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.paritytech.polkadotapp.common.utils.FeatureFlags
import io.paritytech.polkadotapp.common.utils.FeatureOption
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.toWorkerResult
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageRecyclingUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.domain.coinageLogD
import io.paritytech.polkadotapp.feature_coinage_impl.domain.coinageLogW
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import io.paritytech.polkadotapp.common.R as RCommon

@HiltWorker
class CoinageRecyclingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val coinageRecyclingUseCase: CoinageRecyclingUseCase
) : CoroutineWorker(appContext, params) {
    companion object {
        private const val NOTIFICATION_ID = 3
        private const val WORK_ID = "CoinageRecycling"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CoinageRecyclingWorker>(
                repeatInterval = getRepeatIntervalMillis(),
                repeatIntervalTimeUnit = TimeUnit.MILLISECONDS
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            ).build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_ID, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun getRepeatIntervalMillis(): Long {
            val duration = if (FeatureFlags.isEnabled(FeatureOption.SHORT_WORKER_BACKOFF)) 15.minutes else 12.hours
            return duration.inWholeMilliseconds
        }
    }

    override suspend fun doWork(): Result {
        coinageLogD("Recycling worker run started")

        return coinageRecyclingUseCase()
            .logFailure("Failed to recycle coins")
            .onSuccess { coinageLogD("Recycling worker run finished") }
            .onFailure { coinageLogW("Recycling worker run failed: $it") }
            .toWorkerResult()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val title = applicationContext.getString(RCommon.string.coinage_recycling_worker_notification_title)
        val message = applicationContext.getString(RCommon.string.coinage_recycling_worker_notification_message)

        val channelId = createChannel()

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setSmallIcon(RCommon.drawable.ic_upgrade)
            .setTicker(title)
            .setContentText(message)
            .setOngoing(true)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun createChannel(): String {
        val channelId = applicationContext.getString(RCommon.string.workers_notification_channel_id)

        val channel = NotificationChannel(
            channelId,
            applicationContext.getString(RCommon.string.workers_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        return channelId
    }
}
