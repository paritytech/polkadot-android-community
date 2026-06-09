package io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.navigation

import android.net.Uri
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsNavigationType
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsUtils
import io.paritytech.polkadotapp.feature_products_api.model.ProductId

enum class NavigationResult { INTERCEPTED_BY_POLICY, DELEGATE_TO_WEBVIEW }

/**
 * Determines how navigation requests are handled per environment.
 *
 * Receives a pre-classified [DotNsNavigationType] from the WebViewClient caller.
 * Each implementation only handles the dispatch logic, not the classification.
 */
sealed interface NavigationPolicy {
    fun handleNavigation(type: DotNsNavigationType, destination: Uri): NavigationResult

    /**
     * Chat: reject all navigation.
     */
    data object Disabled : NavigationPolicy {
        override fun handleNavigation(type: DotNsNavigationType, destination: Uri): NavigationResult {
            return NavigationResult.INTERCEPTED_BY_POLICY
        }
    }

    /**
     * SPA Browser: same-product navigation loads in the same WebView. Cross-product opens a new SPA session. External is ignored for now.
     */
    class InlineNavigation(
        private val webViewLoader: (url: String) -> Unit,
        private val onCrossProductNavigation: (Uri) -> Unit,
    ) : NavigationPolicy {
        override fun handleNavigation(type: DotNsNavigationType, destination: Uri): NavigationResult {
            return when (type) {
                DotNsNavigationType.SAME_DOTNS_DOMAIN -> {
                    val normalized = DotNsUtils.normalize(destination) ?: destination
                    webViewLoader(normalized.toString())
                    NavigationResult.INTERCEPTED_BY_POLICY
                }
                DotNsNavigationType.CROSS_DOTNS_DOMAIN -> {
                    onCrossProductNavigation(destination)
                    NavigationResult.INTERCEPTED_BY_POLICY
                }
                DotNsNavigationType.EXTERNAL -> NavigationResult.INTERCEPTED_BY_POLICY
            }
        }
    }

    /**
     * Explore: same-domain stays in WebView, cross-domain opens SPA browser.
     */
    class CatalogNavigation(
        private val onProductSelected: (ProductId) -> Unit,
    ) : NavigationPolicy {
        override fun handleNavigation(type: DotNsNavigationType, destination: Uri): NavigationResult {
            return when (type) {
                DotNsNavigationType.SAME_DOTNS_DOMAIN -> NavigationResult.DELEGATE_TO_WEBVIEW

                DotNsNavigationType.CROSS_DOTNS_DOMAIN -> {
                    val productId = ProductId.fromUrl(destination).getOrNull()
                        ?: return NavigationResult.DELEGATE_TO_WEBVIEW
                    onProductSelected(productId)
                    NavigationResult.INTERCEPTED_BY_POLICY
                }
                DotNsNavigationType.EXTERNAL -> NavigationResult.DELEGATE_TO_WEBVIEW
            }
        }
    }
}
