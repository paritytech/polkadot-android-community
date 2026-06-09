package io.paritytech.polkadotapp.feature_backup_impl.domain

import com.google.android.gms.common.api.ApiException
import io.paritytech.polkadotapp.common.domain.errors.BackupDecryptionKeyNotFoundException
import io.paritytech.polkadotapp.feature_backup_api.domain.error.ImportFromBackupError
import kotlinx.coroutines.CancellationException

private const val GOOGLE_SIGN_IN_CANCELLED_STATUS_CODE = 12501

fun Throwable.toImportFromBackupError(): Throwable = when {
    this is ImportFromBackupError -> this

    this is CancellationException -> this

    this is BackupDecryptionKeyNotFoundException -> ImportFromBackupError.NotFound

    isGoogleSignInCancelled() -> ImportFromBackupError.Cancelled

    else -> ImportFromBackupError.Unknown(this)
}

private fun Throwable.isGoogleSignInCancelled(): Boolean =
    this is ApiException && statusCode == GOOGLE_SIGN_IN_CANCELLED_STATUS_CODE
