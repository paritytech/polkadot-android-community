package io.paritytech.polkadotapp.feature_products_impl.domain.webView

import android.content.SharedPreferences
import androidx.core.content.edit
import io.paritytech.polkadotapp.feature_products_api.model.ProductId

/**
 * Debug menu: where a product's app is fetched from instead of its archive, so a SPA served from a
 * laptop runs under the product's real origin.
 *
 * Kept out of the database for the same reason `DebugPocketCards` is: a developer's scratch setting
 * is not app state worth a schema revision.
 */
interface DebugAppOrigins {
    fun get(productId: ProductId): String?

    fun set(productId: ProductId, origin: String?)
}

class PrefsDebugAppOrigins(
    private val prefs: SharedPreferences,
    private val isDebugBuild: Boolean,
) : DebugAppOrigins {
    override fun get(productId: ProductId): String? {
        if (!isDebugBuild) return null

        return prefs.getString(productId.key(), null)?.takeIf { it.isNotBlank() }
    }

    override fun set(productId: ProductId, origin: String?) {
        prefs.edit {
            // A trailing slash would double up against the request path this origin is prefixed to.
            putString(productId.key(), origin?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() })
        }
    }

    private fun ProductId.key() = "$value.$APP_ORIGIN"

    private companion object {
        const val APP_ORIGIN = "app_origin"
    }
}
