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
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Instant
import io.paritytech.polkadotapp.common.R as RCommon

internal suspend inline fun <reified W : ListenableWorker> enqueueExpeditedTransfer(
    context: Context,
    workId: String,
    passLocks: HopTransferPassLocks
): Result<Unit> {
    val request = OneTimeWorkRequestBuilder<W>()
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .setConstraints(networkConnected())
        .build()

    return enqueueTransferPass(context, workId, request, passLocks)
}

internal suspend inline fun <reified W : ListenableWorker> enqueueQuietTransfer(
    context: Context,
    workId: String,
    passLocks: HopTransferPassLocks
): Result<Unit> {
    val request = OneTimeWorkRequestBuilder<W>()
        .setConstraints(networkConnected())
        .build()

    return enqueueTransferPass(context, workId, request, passLocks)
}

// The read and the enqueue share one lock so that a burst of triggers adds one pass, not one each.
internal suspend fun enqueueTransferPass(
    context: Context,
    workId: String,
    request: OneTimeWorkRequest,
    passLocks: HopTransferPassLocks
): Result<Unit> {
    return runCancellableCatching {
        // The trigger's row is already stored, so a caller that leaves must not take its pass with it.
        withContext(NonCancellable) {
            passLocks.whileQueueing {
                val workManager = WorkManager.getInstance(context)
                val policy = passPolicy(workManager.getWorkInfosForUniqueWorkFlow(workId).first(), Clock.System.now())

                if (policy != null) {
                    workManager.enqueueUniqueWork(workId, policy, request).await()
                }
            }
        }
    }.logFailure("Failed to queue a $workId pass")
}

// Replacing a delayed pass is safe: one still unwinding from a stop is waited out by HopTransferPassLocks.runPass.
internal fun passPolicy(passes: Collection<WorkInfo>, now: Instant): ExistingWorkPolicy? {
    val waiting = passes.filter { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }

    return when {
        waiting.isEmpty() -> ExistingWorkPolicy.APPEND_OR_REPLACE
        waiting.any { it.state == WorkInfo.State.ENQUEUED && it.nextScheduleTimeMillis > now.toEpochMilliseconds() } -> ExistingWorkPolicy.REPLACE
        else -> null
    }
}

// Under its own name, so a pending backoff never holds back the pass a fresh transfer asks for.
internal fun scheduleTransferWakeUp(context: Context, transfer: HopTransfer, executeAt: Instant?) {
    val workManager = WorkManager.getInstance(context)

    if (executeAt == null) {
        workManager.cancelUniqueWork(HopTransferWakeUpWorker.workIdFor(transfer))
    } else {
        val delay = executeAt - Clock.System.now()
        val request = OneTimeWorkRequestBuilder<HopTransferWakeUpWorker>()
            .setInitialDelay(delay.inWholeMilliseconds.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setConstraints(networkConnected())
            .setInputData(HopTransferWakeUpWorker.inputFor(transfer))
            .build()

        workManager.enqueueUniqueWork(HopTransferWakeUpWorker.workIdFor(transfer), ExistingWorkPolicy.REPLACE, request)
    }
}

internal fun networkConnected(): Constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()

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
