package io.paritytech.polkadotapp.feature_products_impl.domain.truapi

import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.feature_account_api.domain.derivation.DerivationIndex32
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.ApAllocatableResource
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_api.model.signing.SigningRequestBody

/**
 * A core-initiated action awaiting the user's yes/no.
 *
 * Each variant carries exactly what its native route needs — domain types
 * rather than the core's FFI ones, so a pin bump cannot reach past this
 * boundary. The core owns the action either way: the app only answers.
 */
sealed interface TrUAPIConfirmation {
    /**
     * Signing, in all four of its flavours. Goes to the app's own signing sheet
     * rather than a prompt, so the user sees the decoded call. Confirm-only:
     * the core holds the key and signs after approval.
     */
    class Signing(
        val requesterProductId: String,
        val request: SigningRequestBody,
    ) : TrUAPIConfirmation

    /** Everything that answers through a native prompt rather than the signing sheet. */
    sealed interface Prompt : TrUAPIConfirmation

    /**
     * Sign a Statement Store proof payload with a product account. Native
     * `createStatementProof` gates on access to the signing account's product.
     */
    class StatementSign(
        val callingProductId: ProductId,
        val accountOwner: ProductId,
    ) : Prompt

    /**
     * Derive a contextual alias. Native `getContextualAlias` reuses the
     * persisted account-access grant for the context's product.
     */
    class AccountAlias(
        val callingProductId: ProductId,
        val contextOwner: ProductId,
    ) : Prompt

    /**
     * Create a ring-VRF proof under another product's context. Native
     * `createProof` asks through the one-time cross-product proof prompt.
     */
    class CreateProof(
        val callingProductId: ProductId,
        val contextOwner: ProductId,
        val suffix: DerivationIndex32,
        val message: DataByteArray,
    ) : Prompt

    /** Disclose the user's primary identity. Native `getUserId` gates on the user-identity permission. */
    class IdentityDisclosure(
        val productId: ProductId,
    ) : Prompt

    /**
     * Allocate host resources to the product. Confirmed on the native
     * allocation sheet, but confirm-only: the core performs its own
     * allocation after a yes.
     */
    class ResourceAllocation(
        val callingProductId: ProductId,
        val resources: List<ApAllocatableResource>,
    ) : Prompt

    /** Submit a preimage. Native `submitPreimage` consumes the preimage-submit permission. */
    class PreimageSubmit(
        val callingProductId: ProductId,
    ) : Prompt

    /** Let one product act on another product's account, as native `accountGet` gates it. */
    class AccountAccess(
        val requestingProductId: ProductId,
        val targetProductId: ProductId,
    ) : Prompt
}
