package io.paritytech.polkadotapp.design.components.docklet

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first

suspend fun liftDockWhileKeyboardLeaves(
    cardOpen: Flow<Boolean>,
    keyboardUp: Flow<Boolean>,
    onLift: (Boolean) -> Unit,
) {
    cardOpen.drop(1).collect { open ->
        if (!open && keyboardUp.first()) {
            onLift(true)
            keyboardUp.first { !it }
            onLift(false)
        }
    }
}
