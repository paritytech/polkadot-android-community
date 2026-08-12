package io.paritytech.polkadotapp.feature_products_api.model

import android.net.Uri
import androidx.core.net.toUri
import io.paritytech.polkadotapp.common.utils.Urls
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatExtensionId
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsUtils

// Cannot be value class since it is used in assisted factories (e.g. ProductsScriptExecutorFactory)
// and dagger's assisted factories cannot accept value classes as arguments due to mangling
@ConsistentCopyVisibility
data class ProductId private constructor(val value: String) {
    companion object {
        /**
         * Construct ProductId from the product uri.
         * Uri must be a valid .dot domain as per [DotNsUtils.isDotDomain].
         *
         * Resulting product id will be in the form `<productName>.dot`.
         * Example: `coinflip.dot`
         */
        fun fromUrl(uri: Uri): Result<ProductId> = runCatching {
            val normalizedUri = DotNsUtils.normalize(uri)
                ?: throw IllegalStateException("Not a .dot domain: $uri")
            ProductId(normalizedUri.host!!)
        }

        /**
         * Construct ProductId from its bare `<name>.dot` form, where `<name>` may itself contain
         * subdomains. Example: `coinflip.dot`, `arena.coinflip.dot`.
         */
        fun fromString(value: String): Result<ProductId> {
            return if (PRODUCT_ID_PATTERN.matches(value)) {
                Result.success(ProductId(value))
            } else {
                Result.failure(IllegalArgumentException("Not a .dot product id: $value"))
            }
        }

        /**
         * Reconstruct ProductId from a stored value (e.g. database).
         * Trusts that the value is already a valid .dot domain.
         */
        fun fromStoredValue(value: String): ProductId = ProductId(value)

        private val PRODUCT_ID_PATTERN = Regex("""([a-z0-9-]+\.)+dot""")
    }

    override fun toString(): String {
        return value
    }
}

fun ProductId.toChatExtensionId(): ChatExtensionId {
    return "ProductBot_$value"
}

fun ProductId.toUrl() = Urls.ensureHasProtocolOrHttps(value)

fun ProductId.toUri() = toUrl().toUri()
