package io.paritytech.polkadotapp.feature_products_impl.domain.hostApi

import io.paritytech.polkadotapp.common.BuildConfig
import io.paritytech.polkadotapp.common.utils.FeatureOption
import io.paritytech.polkadotapp.common.utils.isEnabled
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTld
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_api.model.derivation.ReservedProductIds

/**
 * CHAT_PRODUCT_IDENTITY_DEMO: `chat.<tld>` is reserved for governance, so no development build can be
 * served from it. The product served from [sourceProductId] calls the host as `chat.<tld>` instead —
 * account, entropy, statement allowance, storage and permissions all key off the mapped id, while
 * content still loads from the source name. Every other product keeps its own id.
 */
class ChatProductIdentityDemo(private val sourceProductId: String?) {
    fun callingProductId(servedProductId: ProductId, tld: DotNsTld): ProductId {
        val source = sourceProductId?.let { ProductId.fromString(it, tld).getOrNull() } ?: return servedProductId

        return if (servedProductId == source) ReservedProductIds.chat(tld) else servedProductId
    }

    companion object {
        fun fromBuildConfig(): ChatProductIdentityDemo = ChatProductIdentityDemo(
            sourceProductId = BuildConfig.CHAT_PRODUCT_IDENTITY_DEMO_SOURCE
                .takeIf { FeatureOption.CHAT_PRODUCT_IDENTITY_DEMO.isEnabled },
        )
    }
}
