package io.paritytech.polkadotapp.feature_calls_impl.media

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.paritytech.polkadotapp.feature_calls_api.domain.models.CallStatus
import io.paritytech.polkadotapp.feature_calls_impl.state.CallStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class CallWakeLockManager @Inject constructor(
    @ApplicationContext context: Context,
    private val callStateHolder: CallStateHolder,
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val wakeLock: PowerManager.WakeLock? =
        if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                WAKE_LOCK_TAG
            )
        } else {
            null
        }

    context(CoroutineScope)
    fun observe() {
        if (wakeLock == null) return

        combine(
            callStateHolder.observeActiveCall(),
            callStateHolder.observeSpeakerOn(),
        ) { call, speakerOn ->
            call?.status is CallStatus.Connected && !speakerOn
        }
            .distinctUntilChanged()
            .onEach { shouldHold -> if (shouldHold) acquire() else release() }
            .launchIn(this@CoroutineScope)
    }

    fun release() {
        val lock = wakeLock ?: return
        if (lock.isHeld) lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
    }

    private fun acquire() {
        val lock = wakeLock ?: return
        if (!lock.isHeld) lock.acquire()
    }

    private companion object {
        const val WAKE_LOCK_TAG = "Polkadot:CallProximity"
    }
}
