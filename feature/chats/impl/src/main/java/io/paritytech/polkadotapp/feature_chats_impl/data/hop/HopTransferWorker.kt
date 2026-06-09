package io.paritytech.polkadotapp.feature_chats_impl.data.hop

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.paritytech.polkadotapp.common.R as RCommon

/**
 * Drains a queue of pending HOP transfers one at a time, marking each in-progress → done/failed.
 * Enqueued expedited, which on API < 31 runs as a foreground service — hence [getForegroundInfo].
 * Subclasses supply the queue ops and the notification copy.
 */
abstract class HopTransferWorker<T : Any>(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    protected abstract val notificationId: Int

    @get:StringRes
    protected abstract val notificationTitleRes: Int

    @get:StringRes
    protected abstract val notificationMessageRes: Int

    protected abstract suspend fun nextPending(): T?
    protected abstract suspend fun markInProgress(item: T)
    protected abstract suspend fun process(item: T)
    protected abstract suspend fun markDone(item: T)
    protected abstract suspend fun markFailed(item: T, error: Throwable)

    final override suspend fun doWork(): Result {
        while (true) {
            val item = nextPending() ?: return Result.success()
            markInProgress(item)
            runCatching {
                process(item)
                markDone(item)
            }.onFailure { markFailed(item, it) }
        }
    }

    final override suspend fun getForegroundInfo(): ForegroundInfo {
        val channelId = applicationContext.getString(RCommon.string.workers_notification_channel_id)
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(
                channelId,
                applicationContext.getString(RCommon.string.workers_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(applicationContext.getString(notificationTitleRes))
            .setContentText(applicationContext.getString(notificationMessageRes))
            .setSmallIcon(RCommon.drawable.ic_notification_default)
            .setOngoing(true)
            .build()

        return ForegroundInfo(notificationId, notification)
    }
}

/** Enqueues [W] as a unique expedited drain worker (KEEP — coalesces with an in-flight run). */
internal inline fun <reified W : ListenableWorker> enqueueExpeditedDrain(context: Context, workId: String) {
    val request = OneTimeWorkRequestBuilder<W>()
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .build()

    WorkManager.getInstance(context)
        .enqueueUniqueWork(workId, ExistingWorkPolicy.KEEP, request)
}
