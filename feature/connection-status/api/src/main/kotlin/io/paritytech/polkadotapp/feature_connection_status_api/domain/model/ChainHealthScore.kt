package io.paritytech.polkadotapp.feature_connection_status_api.domain.model

/** A metric's verdict: whether the chain met it. */
@JvmInline
value class ChainHealthScore private constructor(private val value: Int) {
    companion object {
        val Perfect = ChainHealthScore(1)
        val Zero = ChainHealthScore(0)
    }
}
