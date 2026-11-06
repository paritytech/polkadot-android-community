package io.paritytech.polkadotapp.feature_device_sync_impl.data.transfer

import io.paritytech.polkadotapp.common.domain.model.AeadKey
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.DeviceTransferConstants
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.DeviceTransferKeySchedule
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.discovery.DeviceTransferMessage
import io.paritytech.polkadotapp.feature_statement_store_api.data.Statement
import io.paritytech.polkadotapp.feature_statement_store_api.data.StatementStoreService
import io.paritytech.polkadotapp.feature_statement_store_api.data.TopicFilter
import io.paritytech.polkadotapp.feature_statement_store_api.domain.StatementStoreMessageProver
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.StatementExpiry
import io.paritytech.polkadotapp.feature_statement_store_api.domain.prepareSignedStatement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

class ReceivedDeviceTransferMessage(
    val message: DeviceTransferMessage,
    val expiresAt: Instant,
)

interface DeviceTransferRequestRepository {
    fun subscribeMessages(keys: DeviceTransferKeySchedule): Flow<Result<List<ReceivedDeviceTransferMessage>>>

    suspend fun publishMessage(message: DeviceTransferMessage, keys: DeviceTransferKeySchedule): Result<Unit>
}

@Singleton
class RealDeviceTransferRequestRepository @Inject constructor(
    private val statementStoreService: StatementStoreService,
    private val proverFactory: StatementStoreMessageProver.Factory,
    private val accountRepository: AccountRepository,
    private val cipher: DeviceTransferMessageCipher,
) : DeviceTransferRequestRepository {
    private val expiryMutex = Mutex()
    private var lastExpiry = 0uL

    override fun subscribeMessages(
        keys: DeviceTransferKeySchedule,
    ): Flow<Result<List<ReceivedDeviceTransferMessage>>> {
        val filter = TopicFilter.MatchAll(listOf(keys.requestTopic.value))

        return statementStoreService.subscribeStatements(filter).map { pageResult ->
            pageResult.map { page -> page.statements.mapNotNull { it.toReceivedMessage(keys.requestKey) } }
        }
    }

    override suspend fun publishMessage(message: DeviceTransferMessage, keys: DeviceTransferKeySchedule): Result<Unit> {
        val topic = keys.requestTopic.value

        return runCancellableCatching {
            val body = Statement.Body(
                channel = topic,
                expiry = nextExpiry(),
                topic1 = topic,
                data = cipher.seal(message, keys.requestKey),
            )
            proverFactory.createKeyPairProver(accountRepository.getWalletAccount()).prepareSignedStatement(body)
        }.flatMap { statementStoreService.submitStatement(it) }
    }

    private suspend fun nextExpiry(): ULong = expiryMutex.withLock {
        StatementExpiry.nextExpiringIn(lastExpiry, DeviceTransferConstants.STATEMENT_LIFETIME)
            .also { lastExpiry = it }
    }

    private fun Statement.toReceivedMessage(requestKey: AeadKey): ReceivedDeviceTransferMessage? {
        val message = cipher.open(body.data, requestKey) ?: return null

        return ReceivedDeviceTransferMessage(
            message = message,
            expiresAt = Instant.fromEpochSeconds((body.expiry shr 32).toLong()),
        )
    }
}
