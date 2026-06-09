package io.paritytech.polkadotapp.feature_coinage_impl.data.derivation

import io.novasama.substrate_sdk_android.encrypt.EncryptionType
import io.novasama.substrate_sdk_android.encrypt.junction.SubstrateJunctionDecoder
import io.novasama.substrate_sdk_android.encrypt.keypair.Keypair
import io.novasama.substrate_sdk_android.encrypt.keypair.substrate.Sr25519Keypair
import io.novasama.substrate_sdk_android.encrypt.keypair.substrate.Sr25519SubstrateKeypairFactory
import io.novasama.substrate_sdk_android.encrypt.keypair.substrate.SubstrateKeypairFactory
import io.novasama.substrate_sdk_android.encrypt.seed.substrate.SubstrateSeedFactory
import io.paritytech.polkadotapp.chains.util.deriveSeed32
import io.paritytech.polkadotapp.common.domain.model.intoAccountId
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.AccountSecretsStorage
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.requireMetaAccountPassphrase
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.DerivationIndex
import javax.inject.Inject

class CoinKeypairDerivation @Inject constructor(
    private val accountRepository: AccountRepository,
    private val accountSecretsStorage: AccountSecretsStorage,
) {
    companion object {
        private const val COIN_DERIVATION_PATH_BASE = "//pps//coin"

        private fun getCoinDerivationPath(derivationIndex: DerivationIndex) = "$COIN_DERIVATION_PATH_BASE//$derivationIndex"
    }

    suspend fun deriveKeypair(derivationIndex: DerivationIndex): Keypair {
        val accountId = accountRepository.getWalletAccount().id
        val mnemonic = accountSecretsStorage.requireMetaAccountPassphrase(accountId)

        val path = getCoinDerivationPath(derivationIndex)

        val seedResult = SubstrateSeedFactory.deriveSeed32(mnemonic.words, password = null)
        val keypair = SubstrateKeypairFactory.generate(EncryptionType.SR25519, seedResult.seed, path)

        return keypair
    }

    suspend fun deriveKeypairs(derivationIndices: List<DerivationIndex>): List<Keypair> {
        val accountId = accountRepository.getWalletAccount().id
        val mnemonic = accountSecretsStorage.requireMetaAccountPassphrase(accountId)
        val seedResult = SubstrateSeedFactory.deriveSeed32(mnemonic.words, password = null)

        val baseCoinageKeypair = SubstrateKeypairFactory.generate(EncryptionType.SR25519, seedResult.seed, COIN_DERIVATION_PATH_BASE)
            as Sr25519Keypair

        return derivationIndices.map { derivationIndex ->
            val junction = SubstrateJunctionDecoder.decode("//$derivationIndex").junctions.first()
            Sr25519SubstrateKeypairFactory.deriveChild(baseCoinageKeypair, junction)
        }
    }
}

suspend fun CoinKeypairDerivation.getDerivedAccountId(derivationIndex: DerivationIndex) = deriveKeypair(derivationIndex).publicKey.intoAccountId()

suspend fun CoinKeypairDerivation.getDerivedAccountIds(derivationIndices: List<DerivationIndex>) =
    deriveKeypairs(derivationIndices).map { it.publicKey.intoAccountId() }
