package io.paritytech.polkadotapp.feature_products_impl.domain.permissions

import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.models.PermissionDecision
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.models.ProductPermission
import kotlinx.coroutines.CompletableDeferred

class ProductPermissionContext(
    val productId: ProductId,
    val permissions: List<ProductPermission>,
) {
    private val result = CompletableDeferred<PermissionDecision>()
    private val dismissed = CompletableDeferred<Unit>()

    fun deliver(decision: PermissionDecision) {
        result.complete(decision)
    }

    /**
     * The sheet went away. Denies if no decision landed, so a silent dismissal
     * cannot strand the caller, and marks the prompt dismissed so the requester
     * only returns once the sheet is gone. Runs during `onCleared`, so it
     * cannot suspend.
     */
    fun onAbandoned() {
        result.complete(PermissionDecision.Deny)
        dismissed.complete(Unit)
    }

    suspend fun awaitDecision(): PermissionDecision = result.await()

    suspend fun awaitDismissal() = dismissed.await()
}
