package io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase

import io.novasama.substrate_sdk_android.runtime.extrinsic.builder.ExtrinsicBuilder
import io.paritytech.polkadotapp.bandersnatch_crypto.memberKey
import io.paritytech.polkadotapp.bandersnatch_crypto.sign
import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.util.EncodedArguments.Companion.autoEncodedArgs
import io.paritytech.polkadotapp.chains.util.Modules
import io.paritytech.polkadotapp.chains.util.call
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.coerceToUnit
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.VoucherAllocator
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.breakdownRoundDown
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.CoinageTransactionService
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageOperationGroupId
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageTransactionRequest
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.OwnAsset
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinAmountBreakdownUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.OnboardingUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.data.derivation.VoucherRingDerivation
import io.paritytech.polkadotapp.feature_coinage_impl.data.signer.origins.CoinageTransactionOrigins
import io.paritytech.polkadotapp.feature_transactions.api.data.ExtrinsicService
import io.paritytech.polkadotapp.feature_transactions.api.domain.model.TransactionSignerSource
import io.paritytech.polkadotapp.feature_transactions.api.domain.model.accountId
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject

class RealOnboardingUseCase @Inject constructor(
    private val voucherAllocator: VoucherAllocator,
    private val chainRegistry: ChainRegistry,
    private val extrinsicService: ExtrinsicService,
    private val voucherRingDerivation: VoucherRingDerivation,
    private val coinAmountBreakdownUseCase: CoinAmountBreakdownUseCase,
    private val coinageOrigins: CoinageTransactionOrigins,
    private val transactionService: CoinageTransactionService,
) : OnboardingUseCase {
    override suspend fun onboard(
        amount: BigDecimal,
        signerSource: TransactionSignerSource.Signed,
    ): Result<Unit> {
        val chain = chainRegistry.peopleChain()
        val accountId = signerSource.accountId(chain)

        return coinAmountBreakdownUseCase.createCoinAmountBreakdown()
            .map { it.breakdownRoundDown(amount) }
            .flatMap { denominations -> voucherAllocator.allocateAll(denominations) }
            .flatMap { vouchers -> registerVouchers(vouchers, signerSource, accountId, chain) }
            .coerceToUnit()
    }

    /**
     * Vouchers minted out of an external asset: no coinage input, one voucher output each.
     *
     * Built as one nonce-sequenced batch and registered as one unit. Either the whole onboarding is in the
     * ledger or none of it is — a half-registered batch would leave vouchers on chain that nothing is
     * tracking. What becomes of each one afterwards is still decided per transaction by the recovery pass.
     */
    private suspend fun registerVouchers(
        vouchers: List<RecyclerVoucher>,
        signerSource: TransactionSignerSource.Signed,
        accountId: AccountId,
        chain: Chain,
    ): Result<Unit> = runCatching {
        val txOrigin = coinageOrigins.createInfallibleUnpaidSigned(signerSource)

        val extrinsics = extrinsicService.buildExtrinsics(chain, ExtrinsicService.SubmissionOptions()) {
            vouchers.forEach { voucher ->
                extrinsic(txOrigin) { loadRecyclerWithExternalAssetUnpaid(voucher, accountId) }
            }
        }.getOrThrow()

        val groupId = CoinageOperationGroupId(UUID.randomUUID().toString())

        val requests = extrinsics.mapIndexed { index, extrinsic ->
            CoinageTransactionRequest(
                extrinsic = extrinsic,
                inputs = emptyList(),
                outputs = listOf(OwnAsset.Voucher(vouchers[index].ringVrfKeyIndex)),
            )
        }

        transactionService.submitTransactions(requests, groupId).getOrThrow()
    }

    private suspend fun ExtrinsicBuilder.loadRecyclerWithExternalAssetUnpaid(
        voucher: RecyclerVoucher,
        accountId: AccountId,
    ) {
        val voucherEntropy = voucherRingDerivation.deriveBandersnatch(voucher.ringVrfKeyIndex)

        call(
            moduleName = Modules.COINAGE,
            callName = "load_recycler_with_external_asset_unpaid",
            arguments = autoEncodedArgs(
                "preservation" to Preservation.Expendable as Preservation,
                "value" to voucher.recyclerValue,
                "member_key" to voucherEntropy.memberKey(),
                "proof_of_ownership" to voucherEntropy.sign(accountId.value)
            )
        )
    }

    @Serializable
    private sealed class Preservation {
        @Serializable
        data object Expendable : Preservation()
    }
}
