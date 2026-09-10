package io.paritytech.polkadotapp.feature_coinage_impl.domain.installation

import io.novasama.substrate_sdk_android.encrypt.EncryptionType
import io.novasama.substrate_sdk_android.encrypt.MultiChainEncryption
import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.chains.network.binding.WeightV2
import io.paritytech.polkadotapp.chains.network.binding.intoBalance
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.feature_coinage_impl.data.dataStore.AccountDataStoreRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.dataStore.InstallationRegistrationCall
import io.paritytech.polkadotapp.feature_revive_api.ReviveContractApi
import io.paritytech.polkadotapp.feature_revive_api.ReviveDryRun
import io.paritytech.polkadotapp.feature_revive_api.calls.call
import io.paritytech.polkadotapp.feature_revive_api.calls.revive
import io.paritytech.polkadotapp.feature_transactions.api.data.ExtrinsicService
import io.paritytech.polkadotapp.feature_transactions.api.data.FormExtrinsic
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTransactionService
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxId
import io.paritytech.polkadotapp.feature_transactions.api.domain.model.SignedTransactionOrigin
import io.paritytech.polkadotapp.feature_transactions.api.domain.model.TransactionSignerSource
import java.math.BigInteger
import javax.inject.Inject

interface InstallationRegistrationSubmitter {
    suspend fun submitAttempt(target: InstallationRegistrationTarget): Result<DurableTxId>
}

class DataStoreAccountUnmappedError : Exception("The data store account has no Revive address mapping")

class RealInstallationRegistrationSubmitter @Inject constructor(
    private val chainRegistry: ChainRegistry,
    private val knownChains: KnownChains,
    private val dataStoreRepository: AccountDataStoreRepository,
    private val reviveContractApi: ReviveContractApi,
    private val pgasProvisioner: DataStorePgasProvisioner,
    private val extrinsicService: ExtrinsicService,
    private val durableTransactionService: DurableTransactionService,
) : InstallationRegistrationSubmitter {
    override suspend fun submitAttempt(target: InstallationRegistrationTarget): Result<DurableTxId> {
        val chain = chainRegistry.getChain(knownChains.assetHub)

        return dataStoreRepository.registrationCall(target).flatMap { call ->
            val origin = SignedTransactionOrigin(
                TransactionSignerSource.FromKeyPair(call.account.keypair, MultiChainEncryption.Substrate(EncryptionType.SR25519))
            )

            pgasProvisioner.ensureFunded(call.account.accountId)
                .flatMap { ensureMapped(call) }
                .flatMap { reviveContractApi.dryRun(chain.id, call.account.accountId, call.contract, call.input) }
                .flatMap { dryRun ->
                    val formExtrinsic = call.formExtrinsic(dryRun)

                    extrinsicService.estimateFee(chain, origin, formExtrinsic = formExtrinsic)
                        .flatMap { fee ->
                            val required = dryRun.storageDeposit.withMargin().intoBalance() + fee.amount
                            pgasProvisioner.ensureCovers(call.account.accountId, required)
                        }
                        .flatMap { extrinsicService.buildExtrinsic(chain, origin, ExtrinsicService.SubmissionOptions(), formExtrinsic) }
                }
                .flatMap { extrinsic ->
                    durableTransactionService.submit(COINAGE_INSTALLATION_DOMAIN, extrinsic, target.registrationGroup()) {}
                }
        }
    }

    // A dry-run maps the origin for the length of the simulation, so only storage tells whether a real call would pass.
    private suspend fun ensureMapped(call: InstallationRegistrationCall): Result<Unit> {
        return reviveContractApi.isAccountMapped(knownChains.assetHub, call.account.accountId).flatMap { mapped ->
            if (mapped) Result.success(Unit) else Result.failure(DataStoreAccountUnmappedError())
        }
    }

    // The declared limits are what the fee is charged against, so they carry a margin rather than the pallet maximum.
    private fun InstallationRegistrationCall.formExtrinsic(dryRun: ReviveDryRun): FormExtrinsic = {
        revive.call(
            dest = contract,
            weightLimit = dryRun.weightRequired.withMargin(),
            storageDepositLimit = dryRun.storageDeposit.withMargin(),
            data = input,
        )
    }

    private fun WeightV2.withMargin() = WeightV2(refTime = refTime.withMargin(), proofSize = proofSize.withMargin())

    private fun BigInteger.withMargin(): BigInteger = this * MARGIN_PERCENT.toBigInteger() / HUNDRED

    private companion object {
        const val MARGIN_PERCENT = 120
        val HUNDRED: BigInteger = BigInteger.valueOf(100)
    }
}
