package io.paritytech.polkadotapp.feature_device_sync_impl.presentation.initialization

import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.common.presentation.AppInitializer
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_account_api.data.repository.awaitAccountsInitialized
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.activeInstallation.RealActiveInstallationManager
import kotlinx.coroutines.launch
import javax.inject.Inject

internal class ActiveInstallationInitializer @Inject constructor(
    private val accountRepository: AccountRepository,
    private val activeInstallationManager: RealActiveInstallationManager,
) : AppInitializer {
    context(scope: ComputationalScope)
    override fun initialize(): Result<Unit> = runCatching {
        scope.launch {
            accountRepository.awaitAccountsInitialized()
            activeInstallationManager.followActiveInstallation()
        }
    }
}
