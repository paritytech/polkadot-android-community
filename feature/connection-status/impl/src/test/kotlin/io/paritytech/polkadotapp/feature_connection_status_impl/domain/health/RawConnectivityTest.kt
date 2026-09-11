package io.paritytech.polkadotapp.feature_connection_status_impl.domain.health

import io.novasama.substrate_sdk_android.wsrpc.state.SocketStateMachine.State
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RawConnectivityTest {
    @Test
    fun `an online socket keeps its own state`() {
        assertEquals(RawConnectivity.Connected, connectedSocket.toRawConnectivity(deviceOnline = true))
        assertEquals(RawConnectivity.Pending, reconnectingSocket.toRawConnectivity(deviceOnline = true))
        assertEquals(RawConnectivity.Settled, State.Disconnected.toRawConnectivity(deviceOnline = true))
        assertEquals(RawConnectivity.Settled, null.toRawConnectivity(deviceOnline = true))
    }

    @Test
    fun `a device with no internet settles whatever the socket believes`() {
        assertEquals(RawConnectivity.Settled, connectedSocket.toRawConnectivity(deviceOnline = false))
        assertEquals(RawConnectivity.Settled, reconnectingSocket.toRawConnectivity(deviceOnline = false))
        assertEquals(RawConnectivity.Settled, State.Disconnected.toRawConnectivity(deviceOnline = false))
        assertEquals(RawConnectivity.Settled, null.toRawConnectivity(deviceOnline = false))
    }

    @Test
    fun `an offline socket retrying in a loop settles exactly once`() = runTest {
        val online = MutableStateFlow(false)
        val results = collectResolved(online)
        advanceTimeBy(2_000); runCurrent()

        repeat(10) { attempt ->
            sockets.emit(State.Connecting(url = "wss://node-$attempt.test"))
            advanceTimeBy(300); runCurrent()
        }

        assertEquals(listOf(RawConnectivity.Settled), results)
    }

    @Test
    fun `a connectivity blip shorter than the debounce never reaches the output`() = runTest {
        val online = MutableStateFlow(true)
        val results = collectResolved(online)
        sockets.emit(connectedSocket)
        advanceTimeBy(2_000); runCurrent()
        assertEquals(listOf(RawConnectivity.Connected), results)

        online.value = false
        advanceTimeBy(200); runCurrent()
        online.value = true
        advanceTimeBy(2_000); runCurrent()

        assertEquals(listOf(RawConnectivity.Connected), results)
    }

    private lateinit var sockets: MutableSharedFlow<State?>

    private fun TestScope.collectResolved(online: MutableStateFlow<Boolean>): List<RawConnectivity> {
        sockets = MutableSharedFlow(replay = 1, extraBufferCapacity = 64)
        sockets.tryEmit(null)
        val results = mutableListOf<RawConnectivity>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            rawConnectivity(sockets, online, networkDebounce = 1.seconds).collect { results += it }
        }
        return results
    }

    private val connectedSocket: State = mock(State.Connected::class.java)

    private val reconnectingSocket: State = State.Connecting(url = "wss://example.test")
}
