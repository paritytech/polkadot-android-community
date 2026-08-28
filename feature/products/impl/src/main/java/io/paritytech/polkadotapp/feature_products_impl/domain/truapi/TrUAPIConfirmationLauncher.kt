package io.paritytech.polkadotapp.feature_products_impl.domain.truapi

import io.paritytech.polkadotapp.feature_products_api.model.signing.SigningAccount
import io.paritytech.polkadotapp.feature_products_api.model.signing.SigningContextHolder
import io.paritytech.polkadotapp.feature_products_api.model.signing.SigningRequestBody
import io.paritytech.polkadotapp.feature_products_impl.domain.crossProductProof.CrossProductProofRequester
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.ProductPermissionGuard
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.models.ProductPermission
import io.paritytech.polkadotapp.feature_products_impl.domain.resourceAllocationRequest.ResourceAllocationRequestContextHolder
import io.paritytech.polkadotapp.feature_products_impl.presentation.productBotManagement.ProductsRouter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes a core-initiated confirmation to the exact native flow the equivalent
 * host API call would take, and awaits the user's decision. The core awaits on
 * a blocking-pool thread, and suspending there does not stall other TrUAPI
 * traffic, so a prompt may stay open for as long as the user takes.
 *
 * Permission-gated reviews go through [ProductPermissionGuard], so grants
 * persist and short-circuit exactly as they do natively — an already-granted
 * permission answers yes with no prompt at all.
 */
// Singleton because the holders are: a launcher per bridge would mean one mutex
// per tab, and up to MAX_LIVE_TABS + Explore bridges racing on the shared holders.
@Singleton
class TrUAPIConfirmationLauncher @Inject constructor(
    private val signingContextHolder: SigningContextHolder,
    private val resourceAllocationContextHolder: ResourceAllocationRequestContextHolder,
    private val permissionGuard: ProductPermissionGuard,
    private val crossProductProofRequester: CrossProductProofRequester,
    private val productsRouter: ProductsRouter,
) {
    // The core fires confirmUserAction concurrently and each holder carries one
    // context, so an overlapping sheet-backed request would overwrite it and
    // strand the first caller. Only the two flows that park a context here take
    // it: the permission guard and the proof requester serialize themselves,
    // and an already-granted permission must answer instantly, as natively,
    // rather than queue behind another bridge's open sheet.
    private val oneSheetAtATime = Mutex()

    suspend fun awaitDecision(confirmation: TrUAPIConfirmation): Boolean = when (confirmation) {
        is TrUAPIConfirmation.Signing -> oneSheetAtATime.withLock { awaitSigningDecision(confirmation) }
        is TrUAPIConfirmation.Prompt -> awaitPromptDecision(confirmation)
    }

    private suspend fun awaitPromptDecision(confirmation: TrUAPIConfirmation.Prompt): Boolean = when (confirmation) {
        is TrUAPIConfirmation.StatementSign -> permissionGuard.requestPermission(
            confirmation.callingProductId,
            ProductPermission.AccountAccess(confirmation.accountOwner.value),
        )

        is TrUAPIConfirmation.AccountAlias -> permissionGuard.requestPermission(
            confirmation.callingProductId,
            ProductPermission.AccountAccess(confirmation.contextOwner.value),
        )

        is TrUAPIConfirmation.CreateProof -> crossProductProofRequester.awaitApproval(
            callingProduct = confirmation.callingProductId,
            onBehalfOf = confirmation.contextOwner,
            suffix = confirmation.suffix,
            message = confirmation.message,
        )

        is TrUAPIConfirmation.IdentityDisclosure -> permissionGuard.requestPermission(
            confirmation.productId,
            ProductPermission.UserIdentityAccess,
        )

        is TrUAPIConfirmation.ResourceAllocation -> awaitResourceAllocationDecision(confirmation)

        is TrUAPIConfirmation.PreimageSubmit -> permissionGuard.consumePermission(
            confirmation.callingProductId,
            ProductPermission.RemotePermission.PreimageSubmitAccess,
        )

        is TrUAPIConfirmation.AccountAccess -> permissionGuard.requestPermission(
            confirmation.requestingProductId,
            ProductPermission.AccountAccess(confirmation.targetProductId.value),
        )
    }

    // The app's own signing sheet, so a core-initiated signature is reviewed the
    // same way a native one is, decoded call and all.
    private suspend fun awaitSigningDecision(confirmation: TrUAPIConfirmation.Signing): Boolean {
        val context = TrUAPISigningContext(
            requesterName = confirmation.requesterProductId,
            signingRequestBody = confirmation.request,
            signingAccount = confirmation.request.signingAccount(),
        )
        signingContextHolder.set(context)
        productsRouter.openSignTransaction()
        val approved = context.await()
        // Holding the lock until the sheet is gone keeps a queued confirmation
        // from pushing its sheet under the one still animating out.
        context.awaitDismissal()
        return approved
    }

    // Nothing to confirm for an empty request; the native path returns early too.
    private suspend fun awaitResourceAllocationDecision(
        confirmation: TrUAPIConfirmation.ResourceAllocation,
    ): Boolean = confirmation.resources.isEmpty() || oneSheetAtATime.withLock {
        val context = TrUAPIResourceAllocationContext(
            productId = confirmation.callingProductId,
            resources = confirmation.resources,
        )
        resourceAllocationContextHolder.set(context)
        productsRouter.openResourceAllocationRequestPrompt()
        val approved = context.await()
        context.awaitDismissal()
        approved
    }
}

private fun SigningRequestBody.signingAccount(): SigningAccount = when (this) {
    is SigningRequestBody.ProductAccountSigning -> SigningAccount.Product(account)
    is SigningRequestBody.LegacyAccountSigning -> SigningAccount.Legacy(account)
}
