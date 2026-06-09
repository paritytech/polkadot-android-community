package io.paritytech.polkadotapp.feature_backup_impl.recover

import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import io.paritytech.polkadotapp.common.utils.disable
import io.paritytech.polkadotapp.common.utils.enable
import io.paritytech.polkadotapp.common.utils.launchUnit
import io.paritytech.polkadotapp.feature_backup_api.domain.error.ImportFromBackupError
import io.paritytech.polkadotapp.feature_backup_api.presentation.RecoverOptionsPayload
import io.paritytech.polkadotapp.feature_backup_impl.BackupRouter
import io.paritytech.polkadotapp.feature_backup_impl.recover.domain.RecoverOptionsInteractor
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import io.paritytech.polkadotapp.common.R as RCommon

@HiltViewModel
class RecoverOptionsViewModel @Inject constructor(
    private val router: BackupRouter,
    private val interactor: RecoverOptionsInteractor,
    @ApplicationContext
    private val context: Context
) : BaseViewModel(), RecoverOptionsContract {
    override val isRecovering = MutableStateFlow(false)

    override fun onRecoverFromBackup() = launchUnit {
        if (isRecovering.value) return@launchUnit

        isRecovering.enable()

        interactor.importAccountFromBackup()
            .onSuccess {
                router.backWithResult(RecoverOptionsPayload.REQUEST_KEY, RecoverOptionsPayload.Result.IMPORTED_FROM_BACKUP)
            }
            .onFailure { t ->
                isRecovering.disable()

                when (t) {
                    ImportFromBackupError.Cancelled -> Unit
                    ImportFromBackupError.NotFound -> showError(context.getString(RCommon.string.backup_not_found_error))
                    is ImportFromBackupError.Unknown -> showError(t.original)
                    else -> showError(t)
                }
            }
    }

    override fun onImportRecoveryPhrase() {
        if (isRecovering.value) return
        router.openRecoverMnemonic()
    }

    override fun onDismiss() {
        router.back()
    }
}
