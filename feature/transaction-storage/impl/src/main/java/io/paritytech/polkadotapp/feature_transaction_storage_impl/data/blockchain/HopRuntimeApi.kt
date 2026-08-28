package io.paritytech.polkadotapp.feature_transaction_storage_impl.data.blockchain

import io.paritytech.polkadotapp.chains.call.RuntimeCallsApi
import io.paritytech.polkadotapp.chains.util.scaleEncodeSerializable
import io.paritytech.polkadotapp.common.domain.model.AccountId

internal const val HOP_RUNTIME_API_SECTION = "HopRuntimeApi"
internal const val HOP_CAN_ACCOUNT_PROMOTE = "can_account_promote"

class HopRuntimeCallsApi(val api: RuntimeCallsApi)

val RuntimeCallsApi.hop: HopRuntimeCallsApi
    get() = HopRuntimeCallsApi(this)

/**
 * Calls `HopRuntimeApi.can_account_promote(who, data_len)`. The current runtime implementation returns
 * `account_has_active_authorization(who)` and does not read `data_len`.
 */
suspend fun HopRuntimeCallsApi.canAccountPromote(who: AccountId, dataLength: UInt): Boolean {
    return api.call(
        section = HOP_RUNTIME_API_SECTION,
        method = HOP_CAN_ACCOUNT_PROMOTE,
        arguments = mapOf(
            "who" to who.scaleEncodeSerializable(),
            "data_len" to dataLength.toLong().toBigInteger()
        ),
        returnBinding = { decoded ->
            requireNotNull(decoded as? Boolean) {
                "$HOP_RUNTIME_API_SECTION.$HOP_CAN_ACCOUNT_PROMOTE returned no boolean"
            }
        }
    )
}
