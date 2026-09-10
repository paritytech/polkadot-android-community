package io.paritytech.polkadotapp.feature_coinage_api.domain.model

/**
 * Whether this installation is recorded on chain, so that a reinstall can find the coins it allocates.
 *
 * Until it is, balance held in this installation's subtree could not be recovered from the seed alone.
 */
sealed interface CoinageAccountBackupStatus {
    data object Registering : CoinageAccountBackupStatus

    /** Not final within the expected time. */
    data object Delayed : CoinageAccountBackupStatus

    /** Was included, then a reorg dropped it; it is being registered again. */
    data object Reverted : CoinageAccountBackupStatus

    data object Completed : CoinageAccountBackupStatus
}

val CoinageAccountBackupStatus.needsAttention: Boolean
    get() = this is CoinageAccountBackupStatus.Delayed || this is CoinageAccountBackupStatus.Reverted
