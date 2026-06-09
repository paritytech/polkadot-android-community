package io.paritytech.polkadotapp.app.root.navigation.splash

import io.paritytech.polkadotapp.app.R
import io.paritytech.polkadotapp.app.root.navigation.BaseNavigator
import io.paritytech.polkadotapp.app.root.navigation.NavigationHolder
import io.paritytech.polkadotapp.feature_splash_impl.presentation.SplashRouter
import javax.inject.Inject

class SplashNavigator @Inject constructor(
    navigationHolder: NavigationHolder
) : BaseNavigator(navigationHolder), SplashRouter {
    override fun openMain() {
        performNavigation(R.id.action_global_to_main_graph)
    }

    override fun openClaimUsername() {
        performNavigation(R.id.action_global_to_claim_username_graph)
    }

    override fun openThemeSelection() {
        performNavigation(R.id.action_global_to_theme_onboarding)
    }
}
