package io.paritytech.polkadotapp.app.root.navigation.username

import io.paritytech.polkadotapp.app.R
import io.paritytech.polkadotapp.app.root.navigation.BaseNavigator
import io.paritytech.polkadotapp.app.root.navigation.LegalUrls
import io.paritytech.polkadotapp.app.root.navigation.NavigationHolder
import io.paritytech.polkadotapp.common.presentation.BrowserNavigator
import io.paritytech.polkadotapp.common.utils.toPayloadBundle
import io.paritytech.polkadotapp.feature_backup_api.presentation.BackupFoundPayload
import io.paritytech.polkadotapp.feature_usernames_impl.presentation.UsernamesRouter
import javax.inject.Inject

class UsernameNavigator @Inject constructor(
    navigationHolder: NavigationHolder,
    private val browserNavigator: BrowserNavigator
) : BaseNavigator(navigationHolder), UsernamesRouter {
    override fun openMain() {
        performNavigation(R.id.action_global_to_main_graph)
    }

    override fun openBackupFound(payload: BackupFoundPayload) {
        performNavigation(
            actionId = R.id.action_claim_username_to_backupFoundBottomSheet,
            args = payload.toPayloadBundle()
        )
    }

    override fun openRecoverOptions() {
        performNavigation(R.id.action_claim_username_to_recoverOptionsBottomSheet)
    }

    override fun openTermsOfUse() {
        browserNavigator.open(LegalUrls.TERMS_OF_USE)
    }

    override fun openPrivacyPolicy() {
        browserNavigator.open(LegalUrls.PRIVACY_POLICY)
    }
}
