package io.paritytech.polkadotapp.feature_settings_impl.domain.settings

import io.paritytech.polkadotapp.feature_chats_api.domain.BlockedContactsRepository
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageAccountBackupStatus
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.CoinageRecyclingStrategySettings
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.RecyclingStrategyType
import io.paritytech.polkadotapp.feature_coinage_api.domain.service.CoinageAccountBackupObserver
import io.paritytech.polkadotapp.tools_backup_api.data.BackupExistsStorage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SettingsInteractor @Inject constructor(
    private val backupExistsStorage: BackupExistsStorage,
    private val blockedContactsRepository: BlockedContactsRepository,
    private val recyclingStrategySettings: CoinageRecyclingStrategySettings,
    private val coinageAccountBackupObserver: CoinageAccountBackupObserver,
) {
    fun observeBackupExists(): Flow<Boolean> {
        return backupExistsStorage.observeBackupExists()
    }

    fun observeCoinageBackupStatus(): Flow<CoinageAccountBackupStatus> = coinageAccountBackupObserver.subscribeStatus()

    fun subscribeHasBlockedContacts(): Flow<Boolean> = blockedContactsRepository.subscribeHasBlockedContacts()

    fun observePrivacyMode(): Flow<RecyclingStrategyType> = recyclingStrategySettings.strategyFlow()

    suspend fun setPrivacyMode(mode: RecyclingStrategyType): Result<Unit> =
        recyclingStrategySettings.setStrategy(mode)
}
