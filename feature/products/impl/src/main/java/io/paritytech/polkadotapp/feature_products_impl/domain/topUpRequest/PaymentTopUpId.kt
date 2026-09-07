package io.paritytech.polkadotapp.feature_products_impl.domain.topUpRequest

/**
 * The product's own name for a top-up, and the only thing that identifies it.
 *
 * Supplied by the product rather than handed back by the host: a product can be terminated between asking
 * for a top-up and recording what it was called, and an id it never saw is one it can never ask about again.
 */
@JvmInline
value class PaymentTopUpId(val value: String)
