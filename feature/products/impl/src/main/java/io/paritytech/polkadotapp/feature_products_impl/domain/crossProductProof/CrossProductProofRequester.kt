package io.paritytech.polkadotapp.feature_products_impl.domain.crossProductProof

import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.feature_account_api.domain.derivation.DerivationIndex32
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_impl.presentation.productBotManagement.ProductsRouter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks the user to let [callingProduct] act under another product's context.
 * A one-time approval with the action's details, never persisted; a product
 * acting under its own context needs no consent.
 */
interface CrossProductProofRequester {
    suspend fun awaitApproval(
        callingProduct: ProductId,
        onBehalfOf: ProductId,
        suffix: DerivationIndex32,
        message: DataByteArray,
    ): Boolean
}

@Singleton
class RealCrossProductProofRequester @Inject constructor(
    private val holder: CrossProductProofContextHolder,
    private val productsRouter: ProductsRouter,
) : CrossProductProofRequester {
    // The holder carries one context, so an overlapping request would
    // overwrite it and strand the first caller.
    private val oneAtATime = Mutex()

    override suspend fun awaitApproval(
        callingProduct: ProductId,
        onBehalfOf: ProductId,
        suffix: DerivationIndex32,
        message: DataByteArray,
    ): Boolean {
        if (onBehalfOf == callingProduct) return true

        return oneAtATime.withLock {
            val context = CrossProductProofContext(callingProduct, onBehalfOf, suffix, message)
            holder.set(context)
            productsRouter.openCrossProductProofPrompt()
            val approved = context.awaitDecision() is CrossProductProofContext.Decision.Approved
            // Holding the lock until the sheet is gone keeps a queued request
            // from pushing its sheet under the one still animating out.
            context.awaitDismissal()
            approved
        }
    }
}
