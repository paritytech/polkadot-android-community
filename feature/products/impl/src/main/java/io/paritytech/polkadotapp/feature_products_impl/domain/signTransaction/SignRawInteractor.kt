package io.paritytech.polkadotapp.feature_products_impl.domain.signTransaction

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.novasama.substrate_sdk_android.extensions.toHexString
import io.paritytech.polkadotapp.chains.util.sign
import io.paritytech.polkadotapp.chains.util.signing.MessageSigningContext
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.feature_products_api.model.signing.RawPayloadContent
import io.paritytech.polkadotapp.feature_products_api.model.signing.SignedTransaction
import io.paritytech.polkadotapp.feature_products_api.model.signing.SigningRawPayload
import io.paritytech.polkadotapp.feature_products_impl.domain.ProductAccountDerivationUseCase
import kotlinx.coroutines.withContext

class SignRawInteractor @AssistedInject constructor(
    @Assisted private val payload: SigningRawPayload,
    private val productAccountDerivationUseCase: ProductAccountDerivationUseCase,
    private val coroutineDispatchers: CoroutineDispatchers,
) : TransactionSignInteractor {
    @AssistedFactory
    interface Factory {
        fun create(payload: SigningRawPayload): SignRawInteractor
    }

    override val account get() = payload.account

    override suspend fun parseSigningContent(): Result<ParsedSigningContent> = runCatching {
        ParsedSigningContent.Raw
    }

    override suspend fun humanReadableRepresentation(): Result<String> = runCatching {
        when (val type = payload.type) {
            is RawPayloadContent.Bytes -> formatSigningBytes(type.data)
            is RawPayloadContent.Payload -> type.data
        }
    }

    override suspend fun sign(): Result<SignedTransaction.Raw> {
        return withContext(coroutineDispatchers.io) {
            productAccountDerivationUseCase.deriveKeypair(payload.account)
                .map { keypair ->
                    val signature = keypair.sign(payload.dataToSign(), MessageSigningContext.generalUntrustedMessage())
                    SignedTransaction.Raw(signature = signature.toDataByteArray())
                }
        }
    }

    private fun formatSigningBytes(bytes: ByteArray): String {
        return runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }
            .getOrElse { bytes.toHexString(withPrefix = true) }
    }

    private fun SigningRawPayload.dataToSign(): ByteArray {
        return when (val t = type) {
            is RawPayloadContent.Bytes -> t.data
            is RawPayloadContent.Payload -> t.data.encodeToByteArray()
        }
    }
}
