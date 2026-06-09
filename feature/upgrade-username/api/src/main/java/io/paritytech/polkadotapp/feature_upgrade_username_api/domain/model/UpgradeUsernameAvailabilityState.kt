package io.paritytech.polkadotapp.feature_upgrade_username_api.domain.model

import io.paritytech.polkadotapp.common.domain.model.AccountId

sealed class UpgradeUsernameAvailabilityState {
    object Free : UpgradeUsernameAvailabilityState()
    object ReservedByUs : UpgradeUsernameAvailabilityState()
    class ReclaimExpiredReservation(val expiredAccounts: List<AccountId>) : UpgradeUsernameAvailabilityState()
    object NotAvailable : UpgradeUsernameAvailabilityState()
}
