package io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.handlerGroups

import io.paritytech.polkadotapp.feature_products_impl.domain.bot.ProductsBotApi
import io.paritytech.polkadotapp.feature_products_impl.domain.jsEngine.ContainerBridge
import kotlinx.coroutines.flow.map

class LocaleHostCalls(
    private val botApi: ProductsBotApi,
) : HostCallHandlerGroup {
    override fun registerOn(bridge: ContainerBridge) {
        bridge.registerSubscription<Unit, LocaleDto>("localeSubscribe") {
            botApi.subscribeLocale().map(::LocaleDto)
        }
    }
}

private data class LocaleDto(val languageTag: String)
