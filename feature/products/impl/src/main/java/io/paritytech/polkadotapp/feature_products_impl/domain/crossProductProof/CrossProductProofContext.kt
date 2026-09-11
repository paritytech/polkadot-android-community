package io.paritytech.polkadotapp.feature_products_impl.domain.crossProductProof

import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.feature_account_api.domain.derivation.DerivationIndex32
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import kotlinx.coroutines.CompletableDeferred

class CrossProductProofContext(
    val callingProduct: ProductId,
    val onBehalfOf: ProductId,
    val suffix: DerivationIndex32,
    val message: DataByteArray,
) {
    sealed interface Decision {
        data object Approved : Decision
        data object Rejected : Decision
    }

    private val decision = CompletableDeferred<Decision>()
    private val dismissed = CompletableDeferred<Unit>()

    fun deliverApproved() {
        decision.complete(Decision.Approved)
    }

    fun deliverRejected() {
        decision.complete(Decision.Rejected)
    }

    /**
     * The sheet went away. Fails closed if no decision landed — a silent
     * dismissal must still answer, or the caller waits forever — and marks the
     * prompt dismissed so the requester only returns once the sheet is gone.
     * Runs during `onCleared`, so it cannot suspend.
     */
    fun onAbandoned() {
        decision.complete(Decision.Rejected)
        dismissed.complete(Unit)
    }

    suspend fun awaitDecision(): Decision = decision.await()

    suspend fun awaitDismissal() = dismissed.await()
}
