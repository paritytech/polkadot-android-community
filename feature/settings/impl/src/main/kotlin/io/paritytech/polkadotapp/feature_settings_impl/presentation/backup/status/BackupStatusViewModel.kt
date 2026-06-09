package io.paritytech.polkadotapp.feature_settings_impl.presentation.backup.status

import dagger.hilt.android.lifecycle.HiltViewModel
import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import io.paritytech.polkadotapp.common.utils.launchUnit
import io.paritytech.polkadotapp.feature_backup_api.presentation.BackupConflictPayload
import io.paritytech.polkadotapp.feature_settings_impl.SettingsRouter
import io.paritytech.polkadotapp.feature_settings_impl.domain.interactors.BackupStatusInteractor
import io.paritytech.polkadotapp.feature_settings_impl.presentation.backup.status.models.BackupStatusUiState
import io.paritytech.polkadotapp.tools_authentication_api.domain.AuthenticationCancelledException
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class BackupStatusViewModel @Inject constructor(
    private val router: SettingsRouter,
    private val interactor: BackupStatusInteractor,
) : BaseViewModel(), BackupStatusContract {
    override val state = MutableStateFlow<BackupStatusUiState>(BackupStatusUiState.CheckingForBackup)

    init {
        checkBackupAndInitCurrentState()
    }

    override fun back() {
        router.back()
    }

    override fun onShowMnemonic() = launchUnit {
        interactor.performOneTimeAuthentication()
            .onSuccess {
                router.openMnemonic()
            }
            .onFailure {
                if (it !is AuthenticationCancelledException) {
                    showError(it)
                }
            }
    }

    override fun onCreateBackup() = launchUnit {
        state.value = BackupStatusUiState.BackupInProgress

        interactor.saveBackup()
            .onSuccess {
                state.value = BackupStatusUiState.BackupExists
            }
            .onFailure {
                showError(it)
                state.value = BackupStatusUiState.NoBackup
            }
    }

    override fun onAllowGoogleDrive() {
        checkBackupAndInitCurrentState()
    }

    override fun onDeclineGoogleDrive() {
        router.back()
    }

    override fun onBackupOverriden() {
        state.value = BackupStatusUiState.BackupExists
    }

    override fun onOverrideBackup() {
        (state.value as? BackupStatusUiState.BackupConflict)?.let {
            router.openBackupConflict(BackupConflictPayload(it.createdAt))
        }
    }

    private fun checkBackupAndInitCurrentState() = launchUnit {
        state.value = BackupStatusUiState.CheckingForBackup

        interactor.getRestorableBackup()
            .fold(
                onSuccess = {
                    if (it == null) {
                        state.value = BackupStatusUiState.NoBackup
                        return@fold
                    }

                    if (interactor.hasBackupConflict()) {
                        state.value = BackupStatusUiState.BackupConflict(it.createdAt)
                        router.openBackupConflict(BackupConflictPayload(it.createdAt))
                    } else {
                        state.value = BackupStatusUiState.BackupExists
                    }
                },
                onFailure = {
                    state.value = BackupStatusUiState.NoAccess
                }
            )
    }
}
