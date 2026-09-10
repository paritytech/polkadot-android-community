package io.paritytech.polkadotapp.feature_coinage_impl.domain.service

import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.measureExecution
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.BackupProgress
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageInstallationId
import io.paritytech.polkadotapp.feature_coinage_api.domain.service.CoinageBackupService
import io.paritytech.polkadotapp.feature_coinage_impl.data.dataStore.AccountDataStoreConfigProvider
import io.paritytech.polkadotapp.feature_coinage_impl.data.dataStore.AccountDataStoreRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.installation.CoinageInstallationRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.installation.PreviousInstallation
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.VoucherRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.storage.DeepRecoveryCompletedStorage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// Recovers balance held under the subtrees of this seed's previous installations.
//
// Only ever reads them: a coin found here is spendable, but new keys are allocated in the current installation
// alone, so nothing found here can move the index this installation hands out next.
class RealCoinageBackupService @Inject constructor(
    private val installationRepository: CoinageInstallationRepository,
    private val dataStoreConfigProvider: AccountDataStoreConfigProvider,
    private val dataStoreRepository: AccountDataStoreRepository,
    private val assetScanner: InstallationAssetScanner,
    private val coinsRepository: CoinRepository,
    private val voucherRepository: VoucherRepository,
    private val deepRecoveryCompletedStorage: DeepRecoveryCompletedStorage,
) : CoinageBackupService {
    private val progress = MutableStateFlow<BackupProgress>(BackupProgress.Unknown)

    override fun subscribeProgress(): Flow<BackupProgress> = progress

    context(scope: ComputationalScope)
    override fun start() {
        scope.launch { recoverNewInstallations() }
    }

    context(scope: ComputationalScope)
    override fun deepSearch() {
        scope.launch {
            if (progress.value.isInProgress()) return@launch

            progress.value = BackupProgress.Deep.Syncing
            installationRepository.getPrevious().forEach { deepScan(it) }
            progress.value = BackupProgress.Deep.Completed
        }
    }

    context(scope: ComputationalScope)
    override fun markAsCompleted() {
        scope.launch {
            if (progress.value.isInProgress()) return@launch

            deepRecoveryCompletedStorage.saveValue(true)
            progress.value = BackupProgress.Completed
        }
    }

    private suspend fun recoverNewInstallations() = measureExecution("Recovering previous installations") {
        // A failed read only delays discovery: installations found on an earlier launch are still scanned.
        dataStoreConfigProvider.contractAddress()
            .flatMap { contract -> dataStoreRepository.fetchRegisteredInstallations(contract, at = null) }
            .onSuccess { installationRepository.addPrevious(it) }
            .onFailure { Timber.w(it, "Could not read registered installations, scanning the ones already known") }

        val previous = installationRepository.getPrevious()
        val pending = previous.filterNot { it.initialScanCompleted }

        if (pending.isNotEmpty()) {
            progress.value = BackupProgress.Initial.Syncing
            pending.forEach { initialScan(it) }
            // Newly found balance is worth another look, even if the last one was acknowledged.
            deepRecoveryCompletedStorage.saveValue(false)
        }

        progress.value = when {
            deepRecoveryCompletedStorage.requireValue() -> BackupProgress.Completed
            previous.isEmpty() -> BackupProgress.Completed
            else -> BackupProgress.Initial.Completed
        }
    }

    private suspend fun initialScan(installation: PreviousInstallation) = coroutineScope {
        val coins = async { gapScanCoins(installation.id, installation.coinScanNextIndex, ScanLimit.UntilGap) }
        val vouchers = async { gapScanVouchers(installation.id, installation.voucherScanNextIndex, ScanLimit.UntilGap) }

        val coinsScanned = coins.await()
        val vouchersScanned = vouchers.await()

        if (coinsScanned && vouchersScanned) installationRepository.markInitialScanCompleted(installation.id)
    }

    private suspend fun deepScan(installation: PreviousInstallation) = coroutineScope {
        launch { gapScanCoins(installation.id, installation.coinScanNextIndex, ScanLimit.Batches(DEEP_SEARCH_BATCH_COUNT)) }
        launch { gapScanVouchers(installation.id, installation.voucherScanNextIndex, ScanLimit.Batches(DEEP_SEARCH_BATCH_COUNT)) }
    }

    private suspend fun gapScanCoins(installation: CoinageInstallationId, startIndex: Int, limit: ScanLimit): Boolean {
        return gapScan(startIndex, limit) { batchStart ->
            assetScanner.scanCoins(installation, batchStart, BATCH_SIZE)
                .onSuccess { coinsRepository.saveAll(it) }
                .map { it.isNotEmpty() }
        }
            .onSuccess { installationRepository.updateCoinScanNextIndex(installation, it) }
            .logFailure("Failed to recover coins of a previous installation")
            .isSuccess
    }

    private suspend fun gapScanVouchers(installation: CoinageInstallationId, startIndex: Int, limit: ScanLimit): Boolean {
        return gapScan(startIndex, limit) { batchStart ->
            assetScanner.scanVouchers(installation, batchStart, BATCH_SIZE)
                .onSuccess { voucherRepository.saveAll(it) }
                .map { it.isNotEmpty() }
        }
            .onSuccess { installationRepository.updateVoucherScanNextIndex(installation, it) }
            .logFailure("Failed to recover vouchers of a previous installation")
            .isSuccess
    }

    private suspend fun gapScan(
        startIndex: Int,
        limit: ScanLimit,
        scanBatch: suspend (batchStart: Int) -> Result<Boolean>,
    ): Result<Int> {
        var nextIndex = startIndex
        var batches = 0
        var emptyBatchesInARow = 0

        while (!limit.isReached(batches, emptyBatchesInARow)) {
            val found = scanBatch(nextIndex).getOrElse { return Result.failure(it) }

            emptyBatchesInARow = if (found) 0 else emptyBatchesInARow + 1
            batches += 1
            nextIndex += BATCH_SIZE
        }

        return Result.success(nextIndex)
    }

    private sealed interface ScanLimit {
        fun isReached(batches: Int, emptyBatchesInARow: Int): Boolean

        data object UntilGap : ScanLimit {
            override fun isReached(batches: Int, emptyBatchesInARow: Int) = emptyBatchesInARow >= EMPTY_BATCH_COUNT
        }

        data class Batches(val count: Int) : ScanLimit {
            override fun isReached(batches: Int, emptyBatchesInARow: Int) = batches >= count
        }
    }

    private companion object {
        const val BATCH_SIZE = 500
        const val EMPTY_BATCH_COUNT = 4
        const val DEEP_SEARCH_BATCH_COUNT = 10
    }
}
