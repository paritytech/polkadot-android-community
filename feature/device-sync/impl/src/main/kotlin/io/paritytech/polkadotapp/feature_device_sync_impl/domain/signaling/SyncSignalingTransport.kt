package io.paritytech.polkadotapp.feature_device_sync_impl.domain.signaling

import kotlinx.coroutines.flow.Flow

interface SyncSignalingTransport {
    fun send(envelope: SyncSignalingEnvelope)

    fun subscribeEnvelopes(): Flow<SyncSignalingEnvelope>
}
