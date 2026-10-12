package io.paritytech.polkadotapp.feature_coinage_impl.presentation.mixin

import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.common.utils.stateInBackground
import io.paritytech.polkadotapp.feature_coinage_api.presentation.mixin.CoinageSetupNoticeMixin
import io.paritytech.polkadotapp.feature_coinage_impl.domain.installation.CoinageInstallationGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.update

internal class RealCoinageSetupNoticeMixin(
    scope: ComputationalScope,
    gate: CoinageInstallationGate,
) : CoinageSetupNoticeMixin, ComputationalScope by scope {
    private val dismissals = MutableStateFlow(0)

    override val isVisible: StateFlow<Boolean> = combine(gate.subscribeOnHold(), dismissals, ::Input)
        .scan(Fold.INITIAL) { previous, input -> previous.next(input) }
        .map { it.visible }
        .stateInBackground(SharingStarted.Eagerly, false)

    override fun dismiss() {
        dismissals.update { it + 1 }
    }

    private data class Input(val onHold: Boolean, val dismissals: Int)

    private data class Fold(val visible: Boolean, val seen: Input?) {
        fun next(input: Input): Fold = when {
            seen == null || input.onHold != seen.onHold -> Fold(visible = input.onHold, seen = input)
            input.dismissals != seen.dismissals -> Fold(visible = false, seen = input)
            else -> Fold(visible = visible, seen = input)
        }

        companion object {
            val INITIAL = Fold(visible = false, seen = null)
        }
    }
}
