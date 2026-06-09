package io.paritytech.polkadotapp.feature_products_impl.domain.signTransaction

import com.google.gson.Gson
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.novasama.substrate_sdk_android.extensions.fromHex
import io.novasama.substrate_sdk_android.runtime.RuntimeSnapshot
import io.novasama.substrate_sdk_android.runtime.definitions.types.fromByteArray
import io.novasama.substrate_sdk_android.runtime.definitions.types.generics.GenericCall
import io.novasama.substrate_sdk_android.runtime.extrinsic.signer.SendableExtrinsic
import io.novasama.substrate_sdk_android.runtime.extrinsic.v5.transactionExtension.TransactionExtension
import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.chains.multiNetwork.getChainIdByGenesisHashOrThrow
import io.paritytech.polkadotapp.chains.multiNetwork.getRuntime
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.feature_products_api.domain.sponsoring.TransactionSponsoring
import io.paritytech.polkadotapp.feature_products_api.model.ProductAccountId
import io.paritytech.polkadotapp.feature_products_api.model.signing.SignedTransaction
import io.paritytech.polkadotapp.feature_products_api.model.signing.createTransaction.TxPayload
import io.paritytech.polkadotapp.feature_products_impl.domain.origin.ProductAccountOrigins
import io.paritytech.polkadotapp.feature_transactions.api.data.ExtrinsicService
import io.paritytech.polkadotapp.feature_transactions.api.data.extensions.DecodedTransactionExtensionValue
import io.paritytech.polkadotapp.feature_transactions.api.data.extensions.TxPayloadExtensionsResolver
import io.paritytech.polkadotapp.feature_transactions.api.di.ExtrinsicSerializer
import io.paritytech.polkadotapp.feature_transactions.api.domain.model.TransactionOrigin
import kotlinx.coroutines.withContext

class CreateTransactionInteractor @AssistedInject constructor(
    @Assisted private val payload: TxPayload<ProductAccountId>,
    @param:ExtrinsicSerializer private val extrinsicSerializerGson: Gson,
    private val chainRegistry: ChainRegistry,
    private val extrinsicService: ExtrinsicService,
    private val productAccountOrigins: ProductAccountOrigins,
    private val coroutineDispatchers: CoroutineDispatchers,
    private val transactionSponsoring: TransactionSponsoring,
    private val extensionsResolver: TxPayloadExtensionsResolver,
) : TransactionSignInteractor {
    @AssistedFactory
    interface Factory {
        fun create(payload: TxPayload<ProductAccountId>): CreateTransactionInteractor
    }

    override val account: ProductAccountId get() = payload.signer

    override suspend fun parseSigningContent(): Result<ParsedSigningContent> = runCatching {
        val runtime = runtime()
        val call = GenericCall.fromByteArray(runtime, payload.callData.value)
        ParsedSigningContent.Transaction(call)
    }

    override suspend fun humanReadableRepresentation(): Result<String> = runCatching {
        val runtime = runtime()
        val resolved = extensionsResolver.resolve(payload.extensions, runtime).getOrThrow()
        val call = GenericCall.fromByteArray(runtime, payload.callData.value)
        val view = createTransactionHumanReadable(call, resolved.allRequestedExtensions)
        extrinsicSerializerGson.toJson(view)
    }

    override suspend fun sign(): Result<SignedTransaction.GeneralTransaction> {
        return withContext(coroutineDispatchers.io) {
            prepareExtrinsicBuildingContext()
                .flatMap { context ->
                    trySponsorTransaction(context)
                        .flatMap { buildExtrinsic(context) }
                }
                .mapCatching {
                    SignedTransaction.GeneralTransaction(it.extrinsicHex.fromHex().toDataByteArray())
                }
        }
    }

    private suspend fun trySponsorTransaction(context: ExtrinsicBuildingContext): Result<Unit> {
        return transactionSponsoring.sponsorTransaction(
            chainId = context.chain.id,
            call = context.call,
            account = payload.signer,
        )
    }

    private suspend fun resolveTransactionOrigin(): Result<TransactionOrigin> {
        return productAccountOrigins.productAccountOrigin(payload.signer)
    }

    private suspend fun prepareExtrinsicBuildingContext(): Result<ExtrinsicBuildingContext> {
        val chainId = chainId()
        val chain = chainRegistry.getChain(chainId)
        val runtime = chainRegistry.getRuntime(chainId)

        return resolveTransactionOrigin().flatMap { origin ->
            extensionsResolver.resolve(payload.extensions, runtime).map { resolved ->
                val call = GenericCall.fromByteArray(runtime, payload.callData.value)
                ExtrinsicBuildingContext(
                    chain = chain,
                    origin = origin,
                    call = call,
                    submissionOptions = resolved.submissionOptions,
                    customExtensions = resolved.customExtensions,
                )
            }
        }
    }

    private suspend fun buildExtrinsic(context: ExtrinsicBuildingContext): Result<SendableExtrinsic> {
        val (chain, origin, call, submissionOptions, customExtensions) = context

        return extrinsicService.buildExtrinsic(
            chain = chain,
            origin = origin,
            options = submissionOptions,
            formExtrinsic = {
                call(call)
                customExtensions.forEach { setTransactionExtension(it) }
            }
        )
    }

    private data class ExtrinsicBuildingContext(
        val chain: Chain,
        val origin: TransactionOrigin,
        val call: GenericCall.Instance,
        val submissionOptions: ExtrinsicService.SubmissionOptions,
        val customExtensions: List<TransactionExtension>,
    )

    private suspend fun runtime(): RuntimeSnapshot = chainRegistry.getRuntime(chainId())

    private suspend fun chainId(): ChainId {
        return chainRegistry.getChainIdByGenesisHashOrThrow(payload.genesisHash)
    }
}

private fun createTransactionHumanReadable(
    call: GenericCall.Instance,
    extensions: List<DecodedTransactionExtensionValue>,
): Map<String, Any?> = mapOf(
    "call" to call,
    "extensions" to extensions,
)
