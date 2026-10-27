package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.session

import io.paritytech.polkadotapp.common.utils.InformationSize
import io.paritytech.polkadotapp.common.utils.InformationSize.Companion.kilobytes
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_account_api.domain.model.MetaAccount
import io.paritytech.polkadotapp.feature_account_api.domain.model.SharedSecretDerivationDomain
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.DeviceTransferKeySchedule
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.DeviceTransferKeyScheduleProvider
import io.paritytech.polkadotapp.feature_statement_store_api.data.encryption.CommunicationEncryption
import io.paritytech.polkadotapp.feature_statement_store_api.domain.CommunicationSession
import io.paritytech.polkadotapp.feature_statement_store_api.domain.CommunicationSessionCreator
import io.paritytech.polkadotapp.feature_statement_store_api.domain.MessageCompactor
import io.paritytech.polkadotapp.feature_statement_store_api.domain.StatementStoreMessageProver
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.IncomingRequestResponsePolicy
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.SessionAccount
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.StatementLifetime
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

class DeviceTransferSessionFactoryTest {
    private val keys = DeviceTransferKeySchedule.fromEntropy(ByteArray(16) { 0x01 })
    private val requestId = UUID.fromString("6f1c2a9e-3b4d-4e5f-8a7b-0c1d2e3f4a5b")
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val accountRepository: AccountRepository = mock()
    private val sessionCreator = RecordingSessionCreator()

    private val factory = DeviceTransferSessionFactory(
        keyScheduleProvider = FixedKeyScheduleProvider(keys),
        accountRepository = accountRepository,
        sessionCreatorFactory = RecordingSessionCreatorFactory(sessionCreator),
    )

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `sender session addresses the receiver with the request's key and a short lifetime`() = runBlocking<Unit> {
        withWalletAccount()

        factory.create(requestId, DeviceTransferRole.SENDER, scope).getOrThrow()

        assertEquals(keys.senderLabel, sessionCreator.localAccount.accountId)
        assertEquals(keys.receiverLabel, sessionCreator.remoteAccount.accountId)
        assertEquals(keys.sessionKey(requestId).bytes, sessionCreator.encryption.sharedSecret.bytes)
        assertEquals(StatementLifetime.Limited(15.minutes), sessionCreator.statementLifetime)
        assertEquals(2.kilobytes, sessionCreator.maxStatementSize)
    }

    @Test
    fun `receiver session addresses the sender`() = runBlocking<Unit> {
        withWalletAccount()

        factory.create(requestId, DeviceTransferRole.RECEIVER, scope).getOrThrow()

        assertEquals(keys.receiverLabel, sessionCreator.localAccount.accountId)
        assertEquals(keys.senderLabel, sessionCreator.remoteAccount.accountId)
    }

    private suspend fun withWalletAccount() {
        val walletAccount: MetaAccount = mock()
        whenever(accountRepository.getWalletAccount()).thenReturn(walletAccount)
    }

    private class FixedKeyScheduleProvider(
        private val keys: DeviceTransferKeySchedule,
    ) : DeviceTransferKeyScheduleProvider {
        override suspend fun get(): Result<DeviceTransferKeySchedule> = Result.success(keys)
    }

    private class RecordingSessionCreatorFactory(
        private val sessionCreator: CommunicationSessionCreator,
    ) : CommunicationSessionCreator.Factory {
        override fun create(account: MetaAccount): CommunicationSessionCreator = sessionCreator

        override fun create(prover: StatementStoreMessageProver): CommunicationSessionCreator = sessionCreator
    }

    private class RecordingSessionCreator : CommunicationSessionCreator {
        lateinit var localAccount: SessionAccount.Local
        lateinit var remoteAccount: SessionAccount.Remote
        lateinit var encryption: CommunicationEncryption
        var maxStatementSize: InformationSize? = null
        lateinit var statementLifetime: StatementLifetime

        override fun createSession(
            scope: CoroutineScope,
            localAccount: SessionAccount.Local,
            remoteAccount: SessionAccount.Remote,
            encryption: CommunicationEncryption,
            maxStatementSize: InformationSize,
            compactor: MessageCompactor?,
            responsePolicy: IncomingRequestResponsePolicy,
            statementLifetime: StatementLifetime,
        ): CommunicationSession {
            this.localAccount = localAccount
            this.remoteAccount = remoteAccount
            this.encryption = encryption
            this.maxStatementSize = maxStatementSize
            this.statementLifetime = statementLifetime
            return FakeCommunicationSession(localAccount, remoteAccount)
        }

        override suspend fun createMultiDeviceSession(
            scope: CoroutineScope,
            localAccount: SessionAccount.Local,
            remoteAccount: SessionAccount.Remote,
            perDeviceEncryption: CommunicationEncryption,
            identityChatDomain: SharedSecretDerivationDomain,
            maxStatementSize: InformationSize,
            compactor: MessageCompactor?,
            responsePolicy: IncomingRequestResponsePolicy,
        ): CommunicationSession = FakeCommunicationSession(localAccount, remoteAccount)
    }
}
