package io.paritytech.polkadotapp.feature_account_impl.data.storage.accountSecrets

import io.novasama.substrate_sdk_android.extensions.fromHex
import io.novasama.substrate_sdk_android.extensions.toHexString
import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchEntropy
import io.paritytech.polkadotapp.common.utils.blake2b256
import io.paritytech.polkadotapp.database.dao.MetaAccountDao
import io.paritytech.polkadotapp.database.model.MetaAccountLocal
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.AccountSecretsStorage
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.BandersnatchSecretsStorage
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.requireMetaAccountPassphrase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

private const val BANDERSNATCH_SECRET_NAME = "BandersnatchEntropy"

@Singleton
class RealBandersnatchSecretsStorage @Inject constructor(
    private val accountSecretsStorage: AccountSecretsStorage,
    private val metaAccountDao: MetaAccountDao
) : BandersnatchSecretsStorage {
    private val mutex = Mutex()

    override suspend fun getEntropy(metaId: Long): BandersnatchEntropy = mutex.withLock {
        getEntropyFromStorage(metaId) ?: generateAndSaveEntropy(metaId)
    }

    private suspend fun getEntropyFromStorage(metaId: Long): BandersnatchEntropy? {
        return accountSecretsStorage.getAdditionalMetaAccountSecret(metaId, BANDERSNATCH_SECRET_NAME)?.let {
            BandersnatchEntropy(it.fromHex())
        }
    }

    private suspend fun generateAndSaveEntropy(metaId: Long): BandersnatchEntropy {
        val metaAccount = metaAccountDao.getMetaAccount(metaId)
            ?: error("Meta account with id $metaId not found")
        val purpose = metaAccount.purpose

        if (!isEntropySupported(purpose)) {
            error("Entropy not found and generation not supported for purpose $purpose")
        }

        val mnemonic = accountSecretsStorage.requireMetaAccountPassphrase(metaId)
        val key = purpose.bandersnatchHashingKey()
        val entropyValue = mnemonic.entropy.blake2b256(key)
        val entropy = BandersnatchEntropy(entropyValue)

        accountSecretsStorage.putAdditionalMetaAccountSecret(metaId, BANDERSNATCH_SECRET_NAME, entropy.value.toHexString())

        return entropy
    }

    private fun isEntropySupported(purpose: MetaAccountLocal.PurposeLocal): Boolean {
        return when (purpose) {
            MetaAccountLocal.PurposeLocal.CANDIDATE,
            MetaAccountLocal.PurposeLocal.WALLET -> true

            MetaAccountLocal.PurposeLocal.DEPOSIT,
            MetaAccountLocal.PurposeLocal.ALIAS -> false
        }
    }

    private fun MetaAccountLocal.PurposeLocal.bandersnatchHashingKey(): ByteArray? {
        return when (this) {
            MetaAccountLocal.PurposeLocal.CANDIDATE -> "candidate".toByteArray(Charsets.UTF_8)

            MetaAccountLocal.PurposeLocal.WALLET,
            MetaAccountLocal.PurposeLocal.DEPOSIT,
            MetaAccountLocal.PurposeLocal.ALIAS -> null
        }
    }
}
