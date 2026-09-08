package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.models

import androidx.compose.runtime.Immutable

/**
 * The composition bar's three segments, as value-weighted shares of the total.
 *
 * A direct picture of the three figures printed above it — the same buckets in the same order — which is why
 * the bar needs no legend of its own beyond the swatches that share its fills.
 */
@Immutable
data class CoinageCompositionUiModel(
    val spendableFraction: Float,
    val gainingPrivacyFraction: Float,
    val unavailableFraction: Float,
) {
    /** Nothing held: the bar renders as a bare capsule frame. */
    val isEmpty: Boolean = spendableFraction == 0f && gainingPrivacyFraction == 0f && unavailableFraction == 0f

    companion object {
        val EMPTY = CoinageCompositionUiModel(0f, 0f, 0f)
    }
}
