package io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.handlerGroups

import io.novasama.substrate_sdk_android.extensions.toHexString
import io.paritytech.polkadotapp.common.utils.HexString
import io.paritytech.polkadotapp.feature_products_api.model.ProductAccountId
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.ProductsBotApi
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.CallingProductIdProvider
import io.paritytech.polkadotapp.feature_products_impl.domain.jsEngine.ContainerBridge

class AccountHostCalls(
    private val botApi: ProductsBotApi,
    private val callingProductIdProvider: CallingProductIdProvider,
) : HostCallHandlerGroup {
    override fun registerOn(bridge: ContainerBridge) {
        bridge.registerHandler<AccountGetParams, ProductAccountResponse>("accountGet") { params ->
            botApi.accountGet(callingProductIdProvider.getProductId().getOrThrow(), ProductAccountId(params.productId, params.derivationIndex))
                .map { ProductAccountResponse(it.publicKey) }
        }

        bridge.registerHandler<AccountGetAliasParams, AccountGetAliasResult>("accountGetAlias") { params ->
            botApi.accountGetAlias(callingProductIdProvider.getProductId().getOrThrow(), ProductAccountId(params.productId, params.derivationIndex))
                .map {
                    AccountGetAliasResult(
                        context = it.context.value.toHexString(withPrefix = true),
                        alias = it.alias.value.toHexString(withPrefix = true)
                    )
                }
        }

        bridge.registerHandler<Unit, List<LegacyAccountResponse>>("getLegacyAccounts") {
            botApi.getLegacyAccounts().map { accounts ->
                accounts.map { LegacyAccountResponse(it.publicKey, it.name) }
            }
        }
    }
}

private data class AccountGetParams(val productId: String, val derivationIndex: Int)
private data class ProductAccountResponse(val publicKey: String)
private data class LegacyAccountResponse(val publicKey: String, val name: String?)
private data class AccountGetAliasParams(val productId: String, val derivationIndex: Int)
private data class AccountGetAliasResult(val context: HexString, val alias: HexString)
