package io.paritytech.polkadotapp.feature_coinage_impl.domain.installation

import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageAccountBackupStatus
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageInstallationId
import io.paritytech.polkadotapp.feature_coinage_api.domain.service.CoinageAccountBackupObserver
import io.paritytech.polkadotapp.feature_coinage_impl.data.installation.CoinageInstallationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

interface CoinageInstallationGate {
    suspend fun awaitCurrent(): CoinageInstallationId

    fun subscribeOnHold(): Flow<Boolean>
}

suspend fun CoinageInstallationGate.awaitCurrentResult(): Result<CoinageInstallationId> = runCancellableCatching { awaitCurrent() }

@Singleton
class RealCoinageInstallationGate @Inject constructor(
    private val installationRepository: CoinageInstallationRepository,
    private val backupObserver: CoinageAccountBackupObserver,
) : CoinageInstallationGate {
    private val waiting = MutableStateFlow(0)

    override fun subscribeOnHold(): Flow<Boolean> = waiting.map { it > 0 }.distinctUntilChanged()

    override suspend fun awaitCurrent(): CoinageInstallationId {
        awaitBackupCompleted()

        return installationRepository.getOrCreateCurrent()
    }

    private suspend fun awaitBackupCompleted() {
        var counted = false

        backupObserver.subscribeStatus()
            .onEach { status ->
                if (!counted && status !is CoinageAccountBackupStatus.Completed) {
                    counted = true
                    waiting.update { it + 1 }
                }
            }
            .onCompletion { if (counted) waiting.update { it - 1 } }
            .first { it is CoinageAccountBackupStatus.Completed }
    }
}
