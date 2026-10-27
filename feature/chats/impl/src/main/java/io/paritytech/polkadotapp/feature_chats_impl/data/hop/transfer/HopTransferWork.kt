package io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Instant
import io.paritytech.polkadotapp.common.R as RCommon

// KEEP: a new trigger must not preempt a running transfer pass — the loop picks up newly queued items itself.
internal inline fun <reified W : ListenableWorker> enqueueExpeditedTransfer(context: Context, workId: String) {
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

// KEEP: same non-preemption rule as the expedited variant; quiet transfers never surface a notification.
internal inline fun <reified W : ListenableWorker> enqueueQuietTransfer(context: Context, workId: String) {
    val request = OneTimeWorkRequestBuilder<W>()
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .build()

    WorkManager.getInstance(context)
        .enqueueUniqueWork(workId, ExistingWorkPolicy.KEEP, request)
}

// Kept apart from the pass's unique name, where the starters' KEEP would hold every new transfer behind this delay.
internal fun scheduleTransferWakeUp(context: Context, transfer: HopTransfer, executeAt: Instant) {
    val delay = executeAt - Clock.System.now()
    val request = OneTimeWorkRequestBuilder<HopTransferWakeUpWorker>()
        .setInitialDelay(delay.inWholeMilliseconds.coerceAtLeast(0), TimeUnit.MILLISECONDS)
        .setInputData(HopTransferWakeUpWorker.inputFor(transfer))
        .build()

    WorkManager.getInstance(context)
        .enqueueUniqueWork(HopTransferWakeUpWorker.workIdFor(transfer), ExistingWorkPolicy.REPLACE, request)
}

internal fun hopTransferForegroundInfo(
    context: Context,
    notificationId: Int,
    @StringRes titleRes: Int,
    @StringRes messageRes: Int
): ForegroundInfo {
    val channelId = context.getString(RCommon.string.workers_notification_channel_id)
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.createNotificationChannel(
        NotificationChannel(
            channelId,
            context.getString(RCommon.string.workers_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
    )

    val notification = NotificationCompat.Builder(context, channelId)
        .setContentTitle(context.getString(titleRes))
        .setContentText(context.getString(messageRes))
        .setSmallIcon(RCommon.drawable.ic_notification_default)
        .setOngoing(true)
        .build()

    return ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
}
