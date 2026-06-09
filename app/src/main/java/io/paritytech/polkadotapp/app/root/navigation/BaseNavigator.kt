package io.paritytech.polkadotapp.app.root.navigation

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import io.paritytech.polkadotapp.common.presentation.navigation.ReturnableRouter
import io.paritytech.polkadotapp.common.presentation.navigation.TabRouter
import io.paritytech.polkadotapp.common.presentation.tabs.BottomTab

abstract class BaseNavigator(private val navigationHolder: NavigationHolder) : ReturnableRouter, TabRouter {
    override fun back() {
        navigationHolder.executeBack()
    }

    /**
     * Jetpack navigation specific setResult functionality
     */
    override fun <T : Any> backWithResult(key: String, result: T) {
        val navController = navigationHolder.navController ?: return
        val previousEntry = navController.previousBackStackEntry

        navController.popBackStack()

        previousEntry?.savedStateHandle[key] = result
    }

    /**
     * Performs conditional navigation based on current destination
     * @param cases - array of pairs (currentDestination, navigationAction)
     */
    fun performNavigation(
        cases: Array<Pair<Int, Int>>,
        args: Bundle? = null,
        navOptions: NavOptions? = null
    ) {
        val navController = navigationHolder.navController

        navController?.currentDestination?.let { currentDestination ->
            val (_, case) =
                cases.find { (startDestination, _) -> startDestination == currentDestination.id }
                    ?: throw IllegalArgumentException("Unknown case for ${currentDestination.label}")

            currentDestination.getAction(case)?.let {
                navController.navigate(case, args, navOptions)
            }
        }
    }

    override fun openChatsTab() {
        navigationHolder.requestTab(BottomTab.CHATS)
    }

    override fun openWalletTab() {
        navigationHolder.requestTab(BottomTab.WALLET)
    }

    override fun openExploreTab() {
        navigationHolder.requestTab(BottomTab.EXPLORE)
    }

    override fun openSettingsTab() {
        navigationHolder.requestTab(BottomTab.SETTINGS)
    }

    protected fun performNavigation(
        @IdRes actionId: Int,
        args: Bundle? = null,
        navOptions: NavOptions? = null
    ) {
        val navController = navigationHolder.navController

        navController?.performNavigation(actionId, args, navOptions)
    }

    protected fun popBackstack(@IdRes destinationId: Int, inclusive: Boolean = false) {
        val navController = navigationHolder.navController
        navController?.popBackStack(destinationId, inclusive)
    }

    protected fun NavController.performNavigation(
        @IdRes actionId: Int,
        args: Bundle? = null,
        navOptions: NavOptions? = null
    ) {
        currentDestination?.getAction(actionId)?.let {
            navigate(actionId, args, navOptions)
        }
    }
}
