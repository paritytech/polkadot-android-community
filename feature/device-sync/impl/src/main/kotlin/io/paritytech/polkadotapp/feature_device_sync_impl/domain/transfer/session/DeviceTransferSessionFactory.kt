package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.session

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.X25519PublicKey
import io.paritytech.polkadotapp.common.domain.model.X25519SharedSecret
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.childScope
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_device_sync_impl.data.transfer.PresharedKeyEncryption
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.signaling.SyncPeerChannelSignaling
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.DeviceTransferConstants
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.DeviceTransferKeySchedule
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.DeviceTransferKeyScheduleProvider
import io.paritytech.polkadotapp.feature_statement_store_api.domain.CommunicationSessionCreator
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.SessionAccount
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.StatementLifetime
import kotlinx.coroutines.CoroutineScope
import java.util.UUID
import javax.inject.Inject

class DeviceTransferSessionFactory @Inject constructor(
    private val keyScheduleProvider: DeviceTransferKeyScheduleProvider,
    private val accountRepository: AccountRepository,
    private val sessionCreatorFactory: CommunicationSessionCreator.Factory,
) {
    suspend fun create(
        requestId: UUID,
        role: DeviceTransferRole,
        parentScope: CoroutineScope,
    ): Result<DeviceTransferSession> {
        return keyScheduleProvider.get().flatMap { keys ->
            presharedKeyEncryption(keys, requestId).flatMap { encryption ->
                runCancellableCatching { createSession(keys, requestId, role, encryption, parentScope) }
            }
        }
    }

    private suspend fun createSession(
        keys: DeviceTransferKeySchedule,
        requestId: UUID,
        role: DeviceTransferRole,
        encryption: PresharedKeyEncryption,
        parentScope: CoroutineScope,
    ): DeviceTransferSession {
        val scope = parentScope.childScope(supervised = true)
        val sessionCreator = sessionCreatorFactory.create(accountRepository.getWalletAccount())

        val communicationSession = sessionCreator.createSession(
            scope = scope,
            localAccount = SessionAccount.Local(accountId = keys.ownLabel(role), pin = null),
            remoteAccount = SessionAccount.Remote(
                accountId = keys.peerLabel(role),
                pin = null,
                publicKey = encryption.localPublicKey,
            ),
            encryption = encryption,
            maxStatementSize = DeviceTransferConstants.MAX_STATEMENT_SIZE,
            statementLifetime = StatementLifetime.Limited(DeviceTransferConstants.STATEMENT_LIFETIME),
        )
        val transport = DeviceTransferSessionTransport(communicationSession, scope)
        val signaling = SyncPeerChannelSignaling(transport = transport, scope = scope, onOfferIdDetermined = {})

        return DeviceTransferSession(requestId, role, signaling, transport, scope)
    }

    private fun presharedKeyEncryption(
        keys: DeviceTransferKeySchedule,
        requestId: UUID,
    ): Result<PresharedKeyEncryption> {
        val unusedPublicKey = ByteArray(X25519PublicKey.SIZE_BYTES).toDataByteArray()

        return X25519SharedSecret.fromBytes(keys.sessionKey(requestId).bytes).flatMap { sessionSecret ->
            X25519PublicKey.fromBytes(unusedPublicKey).map { publicKey -> PresharedKeyEncryption(sessionSecret, publicKey) }
        }
    }

    private fun DeviceTransferKeySchedule.ownLabel(role: DeviceTransferRole): AccountId = when (role) {
        DeviceTransferRole.SENDER -> senderLabel
        DeviceTransferRole.RECEIVER -> receiverLabel
    }

    private fun DeviceTransferKeySchedule.peerLabel(role: DeviceTransferRole): AccountId = when (role) {
        DeviceTransferRole.SENDER -> receiverLabel
        DeviceTransferRole.RECEIVER -> senderLabel
    }
}
