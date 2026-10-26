package io.paritytech.polkadotapp.feature_device_sync_impl.data.activeInstallation

import io.paritytech.polkadotapp.common.domain.model.AeadKey
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_device_sync_api.domain.activeInstallation.ActiveInstallation
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.DeviceTransferKeySchedule
import io.paritytech.polkadotapp.feature_statement_store_api.data.Statement
import io.paritytech.polkadotapp.feature_statement_store_api.data.StatementStoreService
import io.paritytech.polkadotapp.feature_statement_store_api.data.StatementsPage
import io.paritytech.polkadotapp.feature_statement_store_api.data.TopicFilter
import io.paritytech.polkadotapp.feature_statement_store_api.domain.StatementStoreMessageProver
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.StatementExpiry
import io.paritytech.polkadotapp.feature_statement_store_api.domain.prepareSignedStatement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ActiveInstallationRecordsPage(
    val records: List<ActiveInstallation>,
    val hasUnreadableRecords: Boolean,
    val isComplete: Boolean,
)

interface ActiveInstallationRecordRepository {
    fun subscribeRecords(keys: DeviceTransferKeySchedule): Flow<Result<ActiveInstallationRecordsPage>>

    suspend fun publishRecord(installation: ActiveInstallation, keys: DeviceTransferKeySchedule): Result<Unit>
}

class RealActiveInstallationRecordRepository @Inject constructor(
    private val statementStoreService: StatementStoreService,
    private val proverFactory: StatementStoreMessageProver.Factory,
    private val accountRepository: AccountRepository,
    private val cipher: ActiveInstallationRecordCipher,
) : ActiveInstallationRecordRepository {
    override fun subscribeRecords(keys: DeviceTransferKeySchedule): Flow<Result<ActiveInstallationRecordsPage>> {
        val filter = TopicFilter.MatchAll(listOf(keys.activeDeviceTopic.value))

        return statementStoreService.subscribeStatements(filter).map { pageResult ->
            pageResult.map { page -> page.toRecordsPage(keys.activeDeviceKey) }
        }
    }

    override suspend fun publishRecord(installation: ActiveInstallation, keys: DeviceTransferKeySchedule): Result<Unit> {
        val topic = keys.activeDeviceTopic.value
        val body = Statement.Body(
            channel = topic,
            expiry = StatementExpiry.createForCurrentTimestamp(),
            topic1 = topic,
            data = cipher.seal(installation, keys.activeDeviceKey),
        )

        return runCancellableCatching {
            val prover = proverFactory.createKeyPairProver(accountRepository.getWalletAccount())
            prover.prepareSignedStatement(body)
        }.flatMap { statementStoreService.submitStatement(it) }
    }

    private fun StatementsPage.toRecordsPage(key: AeadKey): ActiveInstallationRecordsPage {
        val opened = statements.map { cipher.open(it.body.data, key) }

        return ActiveInstallationRecordsPage(
            records = opened.filterNotNull(),
            hasUnreadableRecords = opened.any { it == null },
            isComplete = isComplete,
        )
    }
}
