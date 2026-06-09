package io.paritytech.polkadotapp.feature_products_impl.domain.topUpRequest

import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinPrivateKey
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_transactions.api.domain.model.TransactionSignerSource
import kotlinx.coroutines.CompletableDeferred
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RFC-0006 top-ups are non-rejectable: the user can only claim, so [Outcome] has no Rejected
 * variant. Failures surface as [Outcome.Failed] and propagate back to the host API.
 */
class TopUpRequestContext(
    val productId: ProductId,
    val amount: Balance,
    val source: Source,
) {
    /** How the topped-up funds are obtained when the user claims. */
    sealed interface Source {
        /** Onboard (e.g. load a recycler) using a signer key. */
        data class Onboard(val signerSource: TransactionSignerSource.Signed) : Source

        /** Move already-existing coins into the user's coin set, identified by their secret keys. */
        data class Coins(val coinKeys: List<CoinPrivateKey>) : Source
    }

    sealed interface Outcome {
        data object Claimed : Outcome
        data class Failed(val reason: Throwable) : Outcome
    }

    private val outcome = CompletableDeferred<Outcome>()

    fun deliverClaimed() {
        outcome.complete(Outcome.Claimed)
    }

    fun deliverFailed(reason: Throwable) {
        outcome.complete(Outcome.Failed(reason))
    }

    suspend fun awaitOutcome(): Outcome = outcome.await()
}

@Singleton
class TopUpRequestContextHolder @Inject constructor() {
    private var context: TopUpRequestContext? = null

    fun set(context: TopUpRequestContext) {
        this.context = context
    }

    fun get(): TopUpRequestContext? = context

    fun clear() {
        context = null
    }
}
