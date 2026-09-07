package io.paritytech.polkadotapp.feature_products_impl.domain.topUpRequest

import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A top-up a product asked for. Carried entirely by the id of its coinage group — see [groupId].
 *
 * [startedAt] is what makes the retry window belong to the operation rather than to whichever process
 * happens to be running it: a resumed top-up finishes the window it was given, and is never handed another.
 */
@OptIn(ExperimentalTime::class)
data class TopUpOperation(
    val id: PaymentTopUpId,
    val productId: ProductId,
    val amount: Balance,
    val startedAt: Instant,
)
