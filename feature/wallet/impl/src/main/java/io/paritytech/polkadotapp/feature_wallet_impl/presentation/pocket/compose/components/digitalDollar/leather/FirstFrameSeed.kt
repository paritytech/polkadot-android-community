package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.components.digitalDollar.leather

import java.util.concurrent.atomic.AtomicBoolean

internal class FirstFrameSeed(private val capture: (width: Int, height: Int) -> Unit) {
    private val taken = AtomicBoolean(false)

    fun offer(width: Int, height: Int) {
        if (width > 0 && height > 0 && taken.compareAndSet(false, true)) capture(width, height)
    }
}
