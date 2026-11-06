package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer

import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.AccountSecretsStorage
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.requireMetaAccountPassphrase
import javax.inject.Inject

interface DeviceTransferKeyScheduleProvider {
    suspend fun get(): Result<DeviceTransferKeySchedule>
}

class RealDeviceTransferKeyScheduleProvider @Inject constructor(
    private val accountRepository: AccountRepository,
    private val accountSecretsStorage: AccountSecretsStorage,
) : DeviceTransferKeyScheduleProvider {
    override suspend fun get(): Result<DeviceTransferKeySchedule> = runCancellableCatching {
        val walletAccount = accountRepository.getWalletAccount()
        val mnemonic = accountSecretsStorage.requireMetaAccountPassphrase(walletAccount.id)

        DeviceTransferKeySchedule.fromEntropy(mnemonic.entropy)
    }
}
