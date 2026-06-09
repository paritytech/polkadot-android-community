package io.paritytech.polkadotapp.app.root.navigation.root

import io.paritytech.polkadotapp.app.R
import io.paritytech.polkadotapp.app.root.navigation.BaseNavigator
import io.paritytech.polkadotapp.app.root.navigation.NavigationHolder
import io.paritytech.polkadotapp.app.root.presentation.root.RootRouter
import io.paritytech.polkadotapp.common.utils.toPayloadBundle
import io.paritytech.polkadotapp.feature_products_impl.presentation.spaBrowser.SpaBrowserPayload
import io.paritytech.polkadotapp.feature_videogame_impl.presentation.gameResults.GameResultsPayload
import javax.inject.Inject

class RootNavigator @Inject constructor(
    navigationHolder: NavigationHolder,
) : BaseNavigator(navigationHolder), RootRouter {
    override fun openMain() = performNavigation(R.id.action_global_to_main_graph)

    override fun openClaimUsername() = performNavigation(R.id.action_global_to_claim_username_graph)

    override fun openDebugMenu() = performNavigation(R.id.action_global_to_debug_menu)

    override fun openVideoGame() = performNavigation(R.id.action_global_to_video_game_play_graph)

    override fun openProductBotsManagement() = performNavigation(R.id.action_global_to_product_bots_management)

    override fun openSpaBrowser(url: String) = performNavigation(
        R.id.action_global_to_spaBrowserFragment,
        SpaBrowserPayload(url = url).toPayloadBundle(),
    )

    override fun openSimulatedGameResults(payload: GameResultsPayload) = performNavigation(
        actionId = R.id.action_global_to_gameResultsFragment,
        args = payload.toPayloadBundle()
    )
}
