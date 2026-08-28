package io.paritytech.polkadotapp.feature_products_impl.domain.crossProductProof

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrossProductProofContextHolder @Inject constructor() {
    private var context: CrossProductProofContext? = null

    fun set(context: CrossProductProofContext) {
        this.context = context
    }

    fun get(): CrossProductProofContext? = context

    /**
     * Clears the holder only while it still belongs to [owner]. The prompt's
     * ViewModel is cleared after its dismiss animation, by which time the
     * holder may already carry the next request's context.
     */
    fun clear(owner: CrossProductProofContext) {
        if (context === owner) {
            context = null
        }
    }
}
