package io.paritytech.polkadotapp.feature_backup_api.domain.error

sealed class ImportFromBackupError : Exception() {
    data object NotFound : ImportFromBackupError()
    data object Cancelled : ImportFromBackupError()
    data class Unknown(val original: Throwable) : ImportFromBackupError()
}
