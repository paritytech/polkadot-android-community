package io.paritytech.polkadotapp.feature_coinage_impl.data.derivation

import io.novasama.substrate_sdk_android.encrypt.junction.JunctionType
import io.novasama.substrate_sdk_android.encrypt.junction.SubstrateJunctionDecoder
import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchEntropy
import io.paritytech.polkadotapp.bandersnatch_crypto.memberKey
import io.paritytech.polkadotapp.common.utils.blake2b256
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.AccountSecretsStorage
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.requireMetaAccountPassphrase
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.DerivationIndex
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import javax.inject.Inject

class VoucherRingDerivation @Inject constructor(
    private val accountRepository: AccountRepository,
    private val accountSecretsStorage: AccountSecretsStorage,
) {
    companion object {
        private const val DERIVATION_PATH_BASE = "//pps//ring-vrf"

        private fun getDerivationPath(derivationIndex: DerivationIndex) = "$DERIVATION_PATH_BASE//$derivationIndex"
    }

    suspend fun deriveBandersnatch(derivationIndex: DerivationIndex): BandersnatchEntropy {
        val accountId = accountRepository.getWalletAccount().id
        val mnemonic = accountSecretsStorage.requireMetaAccountPassphrase(accountId)

        return deriveBandersnatchFromEntropy(mnemonic.entropy, derivationIndex)
    }

    suspend fun deriveBandersnatchBatch(derivationIndices: List<DerivationIndex>): List<BandersnatchEntropy> {
        val accountId = accountRepository.getWalletAccount().id
        val mnemonic = accountSecretsStorage.requireMetaAccountPassphrase(accountId)

        val base = deriveBandersnatch(mnemonic.entropy, DERIVATION_PATH_BASE)

        return derivationIndices.map { derivationIndex ->
            val individualPath = "//$derivationIndex"
            deriveBandersnatch(base.value, individualPath)
        }
    }

    private fun deriveBandersnatchFromEntropy(entropy: ByteArray, derivationIndex: DerivationIndex): BandersnatchEntropy {
        val path = getDerivationPath(derivationIndex)
        return deriveBandersnatch(entropy, path)
    }

    private fun deriveBandersnatch(entropy: ByteArray, derivationPath: String): BandersnatchEntropy {
        val decodedPath = SubstrateJunctionDecoder.decode(derivationPath)
        val junctions = decodedPath.junctions

        val derivedEntropy = junctions.fold(entropy) { currentEntropy, junction ->
            require(junction.type == JunctionType.HARD) {
                "Ring-VRF derivation only supports HARD junctions, but found ${junction.type}"
            }

            currentEntropy.blake2b256(junction.chaincode)
        }

        return BandersnatchEntropy(derivedEntropy)
    }
}

suspend fun VoucherRingDerivation.getDerivedMemberKey(derivationIndex: DerivationIndex) =
    deriveBandersnatch(derivationIndex).memberKey()

suspend fun VoucherRingDerivation.getDerivedMemberKeys(derivationIndices: List<DerivationIndex>) =
    deriveBandersnatchBatch(derivationIndices).map { it.memberKey() }

suspend fun VoucherRingDerivation.deriveBandersnatchForVouchers(vouchers: List<RecyclerVoucher>) = vouchers.map {
    deriveBandersnatch(it.ringVrfKeyIndex)
}
