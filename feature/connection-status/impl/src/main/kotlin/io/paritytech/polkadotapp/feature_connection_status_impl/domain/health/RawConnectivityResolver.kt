package io.paritytech.polkadotapp.feature_connection_status_impl.domain.health

import io.novasama.substrate_sdk_android.wsrpc.state.SocketStateMachine.State
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val NETWORK_DEBOUNCE: Duration = 1.seconds

// A socket stuck reconnecting reads as pending forever, whether the node is slow or the phone is
// offline. With no network there is nothing to reconnect to, so the chain counts as down instead.
internal fun State?.toRawConnectivity(deviceOnline: Boolean): RawConnectivity =
    if (deviceOnline) toSocketConnectivity() else RawConnectivity.Settled

// Both operators are load-bearing. A handover drops the active network for a moment while every socket
// stays up, and an offline socket retries several times a second, which would otherwise keep restarting
// the smoother's cooldown before it could report a dead chain.
@OptIn(FlowPreview::class)
internal fun rawConnectivity(
    socketStates: Flow<State?>,
    deviceOnline: Flow<Boolean>,
    networkDebounce: Duration = NETWORK_DEBOUNCE,
): Flow<RawConnectivity> = socketStates
    .combine(deviceOnline.debounce(networkDebounce)) { state, online -> state.toRawConnectivity(online) }
    .distinctUntilChanged()

private fun State?.toSocketConnectivity(): RawConnectivity = when (this) {
    is State.Connected -> RawConnectivity.Connected
    is State.Connecting, is State.WaitingForReconnect -> RawConnectivity.Pending
    null, is State.Disconnected, is State.Paused -> RawConnectivity.Settled
}
