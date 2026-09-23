package io.paritytech.polkadotapp.feature_products_impl.domain.permissions

import io.paritytech.polkadotapp.common.utils.WithdrawableAnswer
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.models.PermissionDecision
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.models.ProductPermission

class ProductPermissionContext(
    val productId: ProductId,
    val permissions: List<ProductPermission>,
) {
    private val result = WithdrawableAnswer<PermissionDecision>()

    val isWithdrawn: Boolean
        get() = result.isWithdrawn

    fun deliver(decision: PermissionDecision) {
        result.deliver(decision)
    }

    suspend fun awaitDecision(open: suspend () -> Unit): PermissionDecision = result.await(open)

    suspend fun awaitWithdrawal() = result.awaitWithdrawal()
}
