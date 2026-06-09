package io.paritytech.polkadotapp.app.root.presentation.root

import io.paritytech.polkadotapp.common.presentation.navigation.ReturnableRouter
import io.paritytech.polkadotapp.feature_videogame_impl.presentation.gameResults.GameResultsPayload

interface RootRouter : ReturnableRouter {
    fun openClaimUsername()

    fun openMain()

    fun openDebugMenu()

    fun openVideoGame()

    fun openProductBotsManagement()

    fun openSpaBrowser(url: String)

    /** Debug-only — bypasses the game lifecycle. */
    fun openSimulatedGameResults(payload: GameResultsPayload)
}
