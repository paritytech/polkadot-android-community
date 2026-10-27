package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.session

import io.paritytech.polkadotapp.feature_statement_store_api.domain.CommunicationSession
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.CommunicationSessionEvent
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.CommunicationSessionId
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.EncodedMessage
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.SessionAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeCommunicationSession(
    override val localAccount: SessionAccount,
    override val remoteAccount: SessionAccount,
) : CommunicationSession {
    val events = MutableSharedFlow<CommunicationSessionEvent>(replay = 16)
    val sent = mutableListOf<EncodedMessage>()

    override val incomingSessionId: CommunicationSessionId = ByteArray(32)

    override fun subscribeEvents(): Flow<CommunicationSessionEvent> = events

    override fun sendMessage(message: EncodedMessage) {
        sent += message
    }

    override fun encrypt(data: ByteArray): ByteArray = data

    override fun generateSharedIncomingSessionValue(salt: ByteArray): ByteArray = salt

    override fun generateSharedOutgoingSessionValue(salt: ByteArray): ByteArray = salt
}
