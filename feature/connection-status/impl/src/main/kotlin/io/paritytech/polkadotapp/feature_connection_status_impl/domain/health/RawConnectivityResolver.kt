package io.paritytech.polkadotapp.feature_connection_status_impl.domain.health

import io.novasama.substrate_sdk_android.wsrpc.state.SocketStateMachine.State
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val OFFLINE_SETTLE: Duration = 1.seconds

internal fun rawConnectivity(socketStates: Flow<State?>, deviceOnline: Flow<Boolean>): Flow<RawConnectivity> =
    socketStates
        .combine(deviceOnline.settleLosses()) { state, online -> state.toRawConnectivity(online) }
        .distinctUntilChanged()

// A reconnecting socket reads as pending forever; with no network there is nothing to reconnect to.
internal fun State?.toRawConnectivity(deviceOnline: Boolean): RawConnectivity =
    if (deviceOnline) toSocketConnectivity() else RawConnectivity.Offline

// A handover drops connectivity for a moment while every socket stays up. The first reading waits too:
// the pipeline is rebuilt on each foreground transition, so a stale value arrives here as a first one.
@OptIn(ExperimentalCoroutinesApi::class)
private fun Flow<Boolean>.settleLosses(): Flow<Boolean> = distinctUntilChanged()
    .transformLatest { online ->
        if (!online) delay(OFFLINE_SETTLE)
        emit(online)
    }

private fun State?.toSocketConnectivity(): RawConnectivity = when (this) {
    is State.Connected -> RawConnectivity.Connected
    is State.Connecting, is State.WaitingForReconnect -> RawConnectivity.Pending
    null, is State.Disconnected, is State.Paused -> RawConnectivity.Settled
}
