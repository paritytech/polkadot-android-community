package io.paritytech.polkadotapp.feature_device_sync_impl.domain.signaling

import io.paritytech.polkadotapp.feature_statement_store_api.domain.CommunicationSession
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.CommunicationSessionEvent
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.EncodedMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow

fun CommunicationSession.subscribeNewMessages(): Flow<EncodedMessage> = flow {
    val handledRequestIds = mutableSetOf<String>()

    subscribeEvents()
        .filterIsInstance<CommunicationSessionEvent.NewMessagesReceived>()
        .collect { event ->
            // A re-delivered request was already routed — skip it entirely.
            if (handledRequestIds.add(event.requestId)) {
                event.messages.forEach { emit(it) }
            }
        }
}
