package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.session

import io.paritytech.polkadotapp.feature_device_sync_impl.domain.signaling.SyncPeerChannelSignaling
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

class DeviceTransferSession(
    val signaling: SyncPeerChannelSignaling,
    private val transport: DeviceTransferSessionTransport,
    private val scope: CoroutineScope,
) {
    fun sendRejection() {
        transport.sendRejection()
    }

    suspend fun awaitRejection() {
        transport.awaitRejection()
    }

    fun dispose() {
        scope.cancel()
    }
}
