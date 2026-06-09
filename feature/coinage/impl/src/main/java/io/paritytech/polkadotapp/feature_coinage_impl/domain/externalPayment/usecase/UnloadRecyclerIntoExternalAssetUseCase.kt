package io.paritytech.polkadotapp.feature_coinage_impl.domain.externalPayment.usecase

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.annotations.AsTuple
import io.novasama.substrate_sdk_android.runtime.extrinsic.builder.ExtrinsicBuilder
import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchAlias
import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchPublicKey
import io.paritytech.polkadotapp.bandersnatch_crypto.aliasInContext
import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.chains.network.binding.BlockHash
import io.paritytech.polkadotapp.chains.network.rpc.RpcCalls
import io.paritytech.polkadotapp.chains.util.EncodedArguments.Companion.autoEncodedArgs
import io.paritytech.polkadotapp.chains.util.amountFromPlanks
import io.paritytech.polkadotapp.chains.util.call
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.CoinageBalanceConversionContext
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.VoucherAllocator
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.balance
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerKey
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.isInRecycler
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.recyclerLocationOrThrow
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinAmountBreakdownUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageBalanceConverterUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.data.derivation.VoucherRingDerivation
import io.paritytech.polkadotapp.feature_coinage_impl.data.helpers.FreeUnloadTokenResolver
import io.paritytech.polkadotapp.feature_coinage_impl.data.helpers.UnloadTokenResolverFactory
import io.paritytech.polkadotapp.feature_coinage_impl.data.helpers.createForCollection
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.RecyclerProofDataProvider
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.VoucherRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.signer.context.CoinageSigningContextProvider
import io.paritytech.polkadotapp.feature_coinage_impl.data.signer.origins.CoinageTransactionOrigins
import io.paritytech.polkadotapp.feature_members_api.data.model.RingRevision
import io.paritytech.polkadotapp.feature_people_api.domain.PeopleCollection
import io.paritytech.polkadotapp.feature_people_api.domain.useCase.ActivePeopleCollectionUseCase
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.feature_transactions.api.data.ExtrinsicDispatch
import io.paritytech.polkadotapp.feature_transactions.api.data.ExtrinsicService
import io.paritytech.polkadotapp.feature_transactions.api.data.flattenExecutionFailure
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * Unloads a set of recycler vouchers into an external-asset balance on the given destination
 * account. When [surplus] is zero, dispatches Coinage.unload_recycler_into_external_asset per
 * (exponent, recyclerIndex) group. When non-zero, picks a single group that can carry the
 * surplus and dispatches Coinage.unload_recycler_into_external_asset_and_vouchers for it,
 * folding the surplus back into freshly-minted vouchers in the same call.
 */
interface UnloadRecyclerIntoExternalAssetUseCase {
    suspend fun unload(
        vouchers: List<RecyclerVoucher>,
        destination: AccountId,
        surplus: Balance,
    ): Result<Unit>
}

class RealUnloadRecyclerIntoExternalAssetUseCase @Inject constructor(
    private val rpcCalls: RpcCalls,
    private val extrinsicService: ExtrinsicService,
    private val originFactory: CoinageTransactionOrigins,
    private val coinageSigningContextProvider: CoinageSigningContextProvider,
    private val voucherRingDerivation: VoucherRingDerivation,
    private val recyclerProofDataProvider: RecyclerProofDataProvider,
    private val activePeopleCollectionUseCase: ActivePeopleCollectionUseCase,
    private val unloadTokenResolverFactory: UnloadTokenResolverFactory,
    private val chainRegistry: ChainRegistry,
    private val voucherRepository: VoucherRepository,
    private val voucherAllocator: VoucherAllocator,
    private val coinAmountBreakdownUseCase: CoinAmountBreakdownUseCase,
    private val coinageBalanceConverterUseCase: CoinageBalanceConverterUseCase,
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
) : UnloadRecyclerIntoExternalAssetUseCase {
    override suspend fun unload(
        vouchers: List<RecyclerVoucher>,
        destination: AccountId,
        surplus: Balance,
    ): Result<Unit> {
        validateInputs(vouchers)?.let { return Result.failure(it) }

        val chain = chainRegistry.getChain(chainAssetProvider.chainId())

        return coinageBalanceConverterUseCase.create()
            .flatMap { balanceContext -> prepareGroups(chain, vouchers, surplus, balanceContext) }
            .flatMap { groups ->
                markVouchersUsedLocally(vouchers)
                submitAndHandle(chain, groups, destination)
            }
    }

    private fun validateInputs(vouchers: List<RecyclerVoucher>): Throwable? = when {
        vouchers.isEmpty() -> IllegalArgumentException("UnloadRecyclerIntoExternalAsset.emptyVouchers")
        vouchers.any { !it.isInRecycler() } -> IllegalArgumentException("UnloadRecyclerIntoExternalAsset.missingRecyclerInfo")
        else -> null
    }

    private suspend fun prepareGroups(
        chain: Chain,
        vouchers: List<RecyclerVoucher>,
        surplus: Balance,
        context: CoinageBalanceConversionContext
    ): Result<List<UnloadGroup>> {
        return with(context) {
            prepareGroups(chain, vouchers, surplus)
        }
    }

    context(CoinageBalanceConversionContext)
    private suspend fun prepareGroups(
        chain: Chain,
        vouchers: List<RecyclerVoucher>,
        surplus: Balance,
    ): Result<List<UnloadGroup>> {
        val grouped = vouchers.groupByRecycler()
        val peopleCollection = activePeopleCollectionUseCase.getActivePeopleCollection()
        val resolvedTokens = unloadTokenResolverFactory
            .createForCollection(peopleCollection)
            .resolve(chain.id, grouped.size)
        val revisionBlockHash = rpcCalls.getBlockHash(chain.id)

        return recyclerProofDataProvider
            .getRecyclerRevisions(chain.id, grouped.keys, revisionBlockHash)
            .logFailure("Failed to get recycler revisions")
            .flatMap { revisions ->
                resolveMixedSetup(grouped, surplus).map { mintedSetup ->
                    buildGroups(
                        grouped = grouped,
                        resolvedTokens = resolvedTokens,
                        revisions = revisions,
                        revisionBlockHash = revisionBlockHash,
                        peopleCollection = peopleCollection,
                        mintedSetup = mintedSetup,
                        surplus = surplus,
                    )
                }
            }
    }

    context(CoinageBalanceConversionContext)
    private suspend fun resolveMixedSetup(
        grouped: Map<RecyclerKey, List<RecyclerVoucher>>,
        surplus: Balance,
    ): Result<MintedSetup?> {
        if (surplus.isZero()) return Result.success(null)

        val group = grouped.entries
            .firstOrNull { (key, voucherList) -> key.exponent.balance() * voucherList.size >= surplus }
            ?: return Result.failure(IllegalStateException("UnloadRecyclerIntoExternalAsset: no recycler group large enough to host surplus $surplus"))

        return allocateSurplusVouchers(surplus).map { MintedSetup(group.key, it) }
    }

    private suspend fun allocateSurplusVouchers(surplus: Balance): Result<List<RecyclerVoucher>> {
        val amount = chainAssetProvider.asset().amountFromPlanks(surplus)

        return coinAmountBreakdownUseCase.createCoinAmountBreakdown()
            .mapCatching { it.breakdown(amount) }
            .flatMap { voucherAllocator.allocateAll(it) }
    }

    context(CoinageBalanceConversionContext)
    private fun buildGroups(
        grouped: Map<RecyclerKey, List<RecyclerVoucher>>,
        resolvedTokens: List<FreeUnloadTokenResolver.ResolvedUnloadToken>,
        revisions: Map<RecyclerKey, RingRevision>,
        revisionBlockHash: BlockHash,
        peopleCollection: PeopleCollection,
        mintedSetup: MintedSetup?,
        surplus: Balance,
    ): List<UnloadGroup> = grouped.entries.mapIndexed { index, (key, voucherList) ->
        val mixedOutput = if (key == mintedSetup?.hostKey) {
            val groupTotal = key.exponent.balance() * voucherList.size
            MixedOutput(
                externalAssetAmount = groupTotal - surplus,
                newVouchers = mintedSetup.newVouchers,
            )
        } else {
            null
        }

        UnloadGroup(
            recyclerKey = key,
            vouchers = voucherList,
            resolvedUnloadToken = resolvedTokens[index],
            revision = revisions.getValue(key),
            revisionBlockHash = revisionBlockHash,
            peopleCollection = peopleCollection,
            mixedOutput = mixedOutput,
        )
    }

    private suspend fun submitAndHandle(
        chain: Chain,
        groups: List<UnloadGroup>,
        destination: AccountId,
    ): Result<Unit> {
        val results = coroutineScope {
            groups.map { group -> async { submitGroup(chain, group, destination) } }.awaitAll()
        }
        return handleResults(groups, results)
    }

    private suspend fun submitGroup(
        chain: Chain,
        group: UnloadGroup,
        destination: AccountId,
    ): Result<ExtrinsicDispatch.Ok> {
        val origin = originFactory.createAsUnloadTokenPeopleOrigin(
            vouchers = group.vouchers,
            resolvedUnloadToken = group.resolvedUnloadToken,
            recyclerRevisionBlockHash = group.revisionBlockHash,
            peopleCollection = group.peopleCollection,
        )
        val aliases = buildAliases(group.vouchers)

        return extrinsicService.submitExtrinsicAndAwaitExecution(chain = chain, origin = origin) {
            if (group.mixedOutput != null) {
                unloadRecyclerIntoExternalAssetAndVouchers(group, aliases, destination)
            } else {
                unloadRecyclerIntoExternalAsset(group, aliases, destination)
            }
        }.flattenExecutionFailure()
    }

    private suspend fun handleResults(
        groups: List<UnloadGroup>,
        results: List<Result<*>>,
    ): Result<Unit> {
        val failures = groups.zip(results).filter { (_, r) -> r.isFailure }
        if (failures.isEmpty()) return Result.success(Unit)

        // Roll back usage state for vouchers in failed groups only; succeeded vouchers are consumed on-chain.
        val vouchersToRollback = failures.flatMap { (group, _) -> group.vouchers }
        rollbackUsageState(vouchersToRollback)

        // Freshly-minted vouchers were never registered on-chain when the mixed call fails — give the indices back.
        val mintedToRollback = failures.flatMap { (group, _) -> group.mixedOutput?.newVouchers.orEmpty() }
        if (mintedToRollback.isNotEmpty()) {
            voucherAllocator.deallocate(mintedToRollback.map { it.ringVrfKeyIndex })
        }

        return Result.failure(failures.first().second.exceptionOrNull()!!)
    }

    // --- Supporting helpers ---

    private suspend fun markVouchersUsedLocally(vouchers: List<RecyclerVoucher>) {
        voucherRepository.saveAll(vouchers.map { it.copy(usageState = RecyclerVoucher.UsageState.USED_LOCALLY) })
    }

    private suspend fun rollbackUsageState(vouchers: List<RecyclerVoucher>) {
        voucherRepository.saveAll(vouchers.map { it.copy(usageState = RecyclerVoucher.UsageState.NOT_USED) })
    }

    private suspend fun buildAliases(vouchers: List<RecyclerVoucher>): List<BandersnatchAlias> {
        val aliasContext = coinageSigningContextProvider.recyclerVouchersContext()
        return vouchers.map { voucher ->
            voucherRingDerivation.deriveBandersnatch(voucher.ringVrfKeyIndex).aliasInContext(aliasContext)
        }
    }

    private fun List<RecyclerVoucher>.groupByRecycler(): Map<RecyclerKey, List<RecyclerVoucher>> =
        groupBy { RecyclerKey(it.recyclerValue, it.recyclerLocationOrThrow().recyclerIndex) }
}

private fun ExtrinsicBuilder.unloadRecyclerIntoExternalAsset(
    group: UnloadGroup,
    aliases: List<BandersnatchAlias>,
    destination: AccountId,
): ExtrinsicBuilder = call(
    moduleName = "Coinage",
    callName = "unload_recycler_into_external_asset",
    arguments = autoEncodedArgs(
        "aliases" to aliases,
        "value" to group.recyclerKey.exponent,
        "index" to group.recyclerKey.recyclerIndex,
        "revision" to group.revision,
        "to" to destination,
    )
)

private fun ExtrinsicBuilder.unloadRecyclerIntoExternalAssetAndVouchers(
    group: UnloadGroup,
    aliases: List<BandersnatchAlias>,
    destination: AccountId,
): ExtrinsicBuilder {
    val mixedOutput = requireNotNull(group.mixedOutput) { "mixedOutput required for and_vouchers call" }

    return call(
        moduleName = "Coinage",
        callName = "unload_recycler_into_external_asset_and_vouchers",
        arguments = autoEncodedArgs(
            "aliases" to aliases,
            "value" to group.recyclerKey.exponent,
            "index" to group.recyclerKey.recyclerIndex,
            "revision" to group.revision,
            "to" to destination,
            "external_asset_amount" to mixedOutput.externalAssetAmount,
            "new_vouchers" to mixedOutput.newVouchers.map {
                NewVoucherEntry(value = it.recyclerValue, memberKey = it.ringVrfPublicKey)
            },
        )
    )
}

private data class UnloadGroup(
    val recyclerKey: RecyclerKey,
    val vouchers: List<RecyclerVoucher>,
    val resolvedUnloadToken: FreeUnloadTokenResolver.ResolvedUnloadToken,
    val revision: RingRevision,
    val revisionBlockHash: BlockHash,
    val peopleCollection: PeopleCollection,
    val mixedOutput: MixedOutput?,
)

private data class MixedOutput(
    val externalAssetAmount: Balance,
    val newVouchers: List<RecyclerVoucher>,
)

private data class MintedSetup(
    val hostKey: RecyclerKey,
    val newVouchers: List<RecyclerVoucher>,
)

@AsTuple
@Serializable
private data class NewVoucherEntry(
    val value: ValueExponent,
    val memberKey: BandersnatchPublicKey,
)
