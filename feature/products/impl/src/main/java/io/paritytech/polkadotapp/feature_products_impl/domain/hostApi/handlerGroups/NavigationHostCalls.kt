package io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.handlerGroups

import androidx.core.net.toUri
import io.paritytech.polkadotapp.common.utils.LocalDevHost
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTld
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTldProvider
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsUtils
import io.paritytech.polkadotapp.feature_products_api.model.toUri
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.CallingProductIdProvider
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.navigation.NavigationPolicy
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.navigation.NavigationResult
import io.paritytech.polkadotapp.feature_products_impl.domain.jsEngine.ContainerBridge

class NavigationHostCalls(
    private val navigationPolicy: NavigationPolicy,
    private val callingProductIdProvider: CallingProductIdProvider,
    private val dotNsTldProvider: DotNsTldProvider,
) : HostCallHandlerGroup {
    override fun registerOn(bridge: ContainerBridge) {
        bridge.registerHandler<NavigateToParams, Unit>("navigateTo") { params ->
            val origin = callingProductIdProvider.getProductId().getOrNull()?.toUri()
            val destination = params.destination.toUri()
            // Only a product minted from a local dev url carries one as its identity, so deriving
            // the dev origin from the caller inherits that gate rather than adding a second one.
            val localDevOrigin = origin?.let { LocalDevHost.parseOrigin(it.toString()) }

            // A dev product needs no dotNS name, so its navigation does not wait on the active TLD —
            // which never resolves when the chain config is unavailable.
            val tld: Result<DotNsTld?> = if (localDevOrigin != null) {
                Result.success(null)
            } else {
                dotNsTldProvider.getTld()
            }

            tld.flatMap { resolved ->
                val type = DotNsUtils.classifyNavigation(origin, destination, resolved, localDevOrigin)
                when (navigationPolicy.handleNavigation(type, destination)) {
                    NavigationResult.INTERCEPTED_BY_POLICY -> Result.success(Unit)
                    NavigationResult.DELEGATE_TO_WEBVIEW -> Result.failure(IllegalStateException("Navigation not handled"))
                }
            }
        }
    }
}

private data class NavigateToParams(val destination: String)
