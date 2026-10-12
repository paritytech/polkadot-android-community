package io.paritytech.polkadotapp.feature_coinage_api.presentation.mixin

import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import kotlinx.coroutines.flow.StateFlow

interface CoinageSetupNoticeMixin {
    /** Shown while an allocation is waiting for this installation's backup, until dismissed or the wait ends. */
    val isVisible: StateFlow<Boolean>

    fun dismiss()

    interface Factory {
        fun create(scope: ComputationalScope): CoinageSetupNoticeMixin
    }
}
