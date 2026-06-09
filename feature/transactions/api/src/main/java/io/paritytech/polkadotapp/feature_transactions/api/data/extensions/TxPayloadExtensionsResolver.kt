package io.paritytech.polkadotapp.feature_transactions.api.data.extensions

import io.novasama.substrate_sdk_android.runtime.RuntimeSnapshot
import io.novasama.substrate_sdk_android.runtime.definitions.types.composite.Struct
import io.novasama.substrate_sdk_android.runtime.definitions.types.fromByteArray
import io.novasama.substrate_sdk_android.runtime.definitions.types.generics.DefaultSignedExtensions
import io.novasama.substrate_sdk_android.runtime.definitions.types.generics.Era
import io.novasama.substrate_sdk_android.runtime.extrinsic.v5.transactionExtension.TransactionExtension
import io.novasama.substrate_sdk_android.runtime.metadata.findSignedExtension
import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.chains.network.binding.intoBalance
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_transactions.api.data.ExtrinsicService
import io.paritytech.polkadotapp.feature_transactions.api.data.Mortality
import java.math.BigInteger
import javax.inject.Inject

class TxPayloadExtensionsResolver @Inject constructor() {
    class Resolved(
        /**
         * Every extension from the input list with its `implicit` / `explicit`
         * bytes decoded via the matching runtime-metadata SCALE type. Order
         * matches the input.
         */
        val allRequestedExtensions: List<DecodedTransactionExtensionValue>,
        val submissionOptions: ExtrinsicService.SubmissionOptions,
        val customExtensions: List<TransactionExtension>,
    )

    fun resolve(
        extensions: List<EncodedTransactionExtensionValue>,
        runtime: RuntimeSnapshot,
    ): Result<Resolved> = runCatching {
        val all = ArrayList<DecodedTransactionExtensionValue>(extensions.size)
        val routing = Routing()

        for (ext in extensions) {
            val decoded = decodeExtension(ext, runtime)
            all += decoded
            routing.accept(decoded)
        }

        Resolved(
            allRequestedExtensions = all,
            submissionOptions = routing.toSubmissionOptions(),
            customExtensions = routing.customExtensions,
        )
    }

    private fun decodeExtension(
        ext: EncodedTransactionExtensionValue,
        runtime: RuntimeSnapshot,
    ): DecodedTransactionExtensionValue {
        val meta = runtime.metadata.extrinsic.findSignedExtension(ext.id)
            ?: error("Signed extension '${ext.id}' is not present in runtime metadata")
        val implicit = meta.includedInSignature?.fromByteArray(runtime, ext.implicit.value)
        val explicit = meta.includedInExtrinsic?.fromByteArray(runtime, ext.explicit.value)
        return DecodedTransactionExtensionValue(id = ext.id, implicit = implicit, explicit = explicit)
    }

    /** Accumulates the per-extension contribution to either SubmissionOptions or the custom-extension list. */
    private class Routing {
        var mortality: Mortality? = null
        var nonce: BigInteger? = null
        var tip: Balance = Balance.ZERO
        var metadataHash: DataByteArray? = null
        var specVersion: Int? = null
        var transactionVersion: Int? = null
        val customExtensions = mutableListOf<TransactionExtension>()

        fun accept(decoded: DecodedTransactionExtensionValue) {
            when (decoded.id) {
                DefaultSignedExtensions.CHECK_MORTALITY -> {
                    val era = decoded.explicit as? Era
                        ?: error("CheckMortality explicit must decode to Era")
                    val blockHash = decoded.implicit as? ByteArray
                        ?: error("CheckMortality implicit must decode to ByteArray")
                    mortality = Mortality(era, blockHash.toDataByteArray())
                }
                DefaultSignedExtensions.CHECK_NONCE -> nonce = unwrapBigInteger(decoded.explicit, decoded.id)
                DefaultSignedExtensions.CHECK_TX_PAYMENT -> tip = unwrapBigInteger(decoded.explicit, decoded.id).intoBalance()
                DefaultSignedExtensions.CHECK_SPEC_VERSION -> specVersion = unwrapBigInteger(decoded.implicit, decoded.id).toInt()
                DefaultSignedExtensions.CHECK_TX_VERSION -> transactionVersion = unwrapBigInteger(decoded.implicit, decoded.id).toInt()
                DefaultSignedExtensions.CHECK_METADATA_HASH -> metadataHash = (decoded.implicit as? ByteArray)?.toDataByteArray()
                // Surfaced via allRequestedExtensions; chain registry resolves the runtime from raw bytes during bootstrap.
                DefaultSignedExtensions.CHECK_GENESIS -> Unit
                else -> customExtensions += TransactionExtension(name = decoded.id, implicit = decoded.implicit, explicit = decoded.explicit)
            }
        }

        fun toSubmissionOptions() = ExtrinsicService.SubmissionOptions(
            mortality = mortality,
            nonce = nonce,
            tip = tip,
            metadataHash = metadataHash,
            specVersion = specVersion,
            transactionVersion = transactionVersion,
        )

        private fun unwrapBigInteger(value: Any?, id: String): BigInteger = when (value) {
            is BigInteger -> value
            is Struct.Instance -> value.mapping.values.single() as? BigInteger
                ?: error("Expected BigInteger inside Struct for '$id'")
            else -> error("Cannot unwrap BigInteger from '$id': ${value?.let { it::class.simpleName }}")
        }
    }
}
