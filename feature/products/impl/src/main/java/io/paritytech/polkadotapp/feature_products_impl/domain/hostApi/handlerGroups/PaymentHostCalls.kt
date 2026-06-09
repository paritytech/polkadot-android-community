package io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.handlerGroups

import io.novasama.substrate_sdk_android.extensions.fromHex
import io.paritytech.polkadotapp.chains.network.binding.intoBalance
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.domain.model.intoAccountId
import io.paritytech.polkadotapp.common.utils.HexString
import io.paritytech.polkadotapp.common.utils.flowOfAll
import io.paritytech.polkadotapp.feature_coinage_api.domain.externalPayment.PaymentStatus
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.ProductsBotApi
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.CallingProductIdProvider
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.InsufficientBalanceException
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.PaymentRejectedException
import io.paritytech.polkadotapp.feature_products_impl.domain.jsEngine.ContainerBridge
import io.paritytech.polkadotapp.feature_products_impl.domain.topUpRequest.PaymentTopUpSource
import kotlinx.coroutines.flow.map
import java.math.BigInteger

class PaymentHostCalls(
    private val botApi: ProductsBotApi,
    private val callingProductIdProvider: CallingProductIdProvider,
) : HostCallHandlerGroup {
    override fun registerOn(bridge: ContainerBridge) {
        bridge.registerSubscription<Unit, PaymentBalanceDto>("paymentBalanceSubscribe") {
            flowOfAll {
                val productId = callingProductIdProvider.getProductId().getOrThrow()
                botApi.subscribePaymentBalance(productId)
                    .map { PaymentBalanceDto(available = it.available.value.toString()) }
            }
        }

        bridge.registerHandler<PaymentRequestParams, PaymentReceiptDto>("paymentRequest") { params ->
            val productId = callingProductIdProvider.getProductId().getOrThrow()
            val amount = BigInteger(params.amount).intoBalance()
            val destination = params.destinationHex.fromHex().intoAccountId()
            botApi.requestPayment(productId, amount, destination)
                .map { PaymentReceiptDto(id = it) }
        }

        bridge.registerHandler<PaymentTopUpParams, Unit>("paymentTopUp") { params ->
            val productId = callingProductIdProvider.getProductId().getOrThrow()
            val amount = BigInteger(params.amount).intoBalance()
            val source = params.toDomainSource()
            botApi.topUp(productId, amount, source)
        }

        bridge.registerSubscription<PaymentStatusSubscribeParams, PaymentStatusDto>("paymentStatusSubscribe") { params ->
            flowOfAll {
                val productId = callingProductIdProvider.getProductId().getOrThrow()
                botApi.subscribePaymentStatus(productId, params.paymentId)
                    .map { it.toDto() }
            }
        }
    }
}

private fun PaymentStatus.toDto(): PaymentStatusDto = when (this) {
    PaymentStatus.Processing -> PaymentStatusDto(tag = "Processing", value = null)
    PaymentStatus.Completed -> PaymentStatusDto(tag = "Completed", value = null)
    is PaymentStatus.Failed -> PaymentStatusDto(tag = "Failed", value = reason)
}

private data class PaymentBalanceDto(val available: String)

private data class PaymentRequestParams(
    /** Amount in planks, as a decimal string (to preserve u128 precision across JSON). */
    val amount: String,
    val destinationHex: HexString,
)

private data class PaymentReceiptDto(val id: String)

private data class PaymentTopUpParams(
    /** Amount in planks, as a decimal string (to preserve u128 precision across JSON). */
    val amount: String,
    /** "ProductAccount", "PrivateKey" or "Coins" — discriminator for the flattened source fields below. */
    val sourceTag: String,
    val sourceDerivationIndex: Int? = null,
    val sourceKeyHex: HexString? = null,
    val sourceKeyListHex: List<HexString>? = null,
) {
    fun toDomainSource(): PaymentTopUpSource = when (sourceTag) {
        "ProductAccount" -> PaymentTopUpSource.ProductAccount(
            derivationIndex = requireNotNull(sourceDerivationIndex) {
                "sourceDerivationIndex missing for ProductAccount source"
            },
        )
        "PrivateKey" -> PaymentTopUpSource.PrivateKey(
            DataByteArray(requireNotNull(sourceKeyHex) { "sourceKeyHex missing for PrivateKey source" }.fromHex())
        )
        "Coins" -> PaymentTopUpSource.Coins(
            secretKeys = requireNotNull(sourceKeyListHex) { "sourceKeyListHex missing for Coins source" }
                .map { DataByteArray(it.fromHex()) }
        )
        else -> throw IllegalArgumentException("Unknown top-up source tag: $sourceTag")
    }
}

private data class PaymentStatusSubscribeParams(val paymentId: String)

private data class PaymentStatusDto(val tag: String, val value: String?)

// Referenced to ensure these exception types resolve for the JS container's error-string matching;
// the bridge surfaces Throwable.message as the error payload.
@Suppress("unused")
private val insufficientBalanceMarker = InsufficientBalanceException

@Suppress("unused")
private val paymentRejectedMarker = PaymentRejectedException
