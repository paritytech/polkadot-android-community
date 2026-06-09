package io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase

import io.novasama.substrate_sdk_android.encrypt.keypair.Keypair
import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.filterNotNull
import io.paritytech.polkadotapp.common.utils.getOrEmpty
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.substrateAccountId
import io.paritytech.polkadotapp.common.utils.takeWhileInclusive
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.formatExponentsToBalance
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinPrivateKey
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageTransferDetection
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.deriveKeypair
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.isTerminal
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageBalanceConverterUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageTransferUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.data.model.OnChainCoinInfo
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.domain.coinageLogD
import io.paritytech.polkadotapp.feature_coinage_impl.domain.coinageLogE
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

class RealCoinageTransferUseCase @Inject constructor(
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
    private val coinRepository: CoinRepository,
    private val coinageBalanceConverterUseCase: CoinageBalanceConverterUseCase,
    private val coinageTransferSubmissionUseCase: CoinageTransferSubmissionUseCase
) : CoinageTransferUseCase {
    companion object {
        private val DETECTION_TIMEOUT = 30.seconds
    }

    override suspend operator fun invoke(
        transferCoins: Boolean,
        coinKeys: List<CoinPrivateKey>,
        pastDetection: CoinageTransferDetection?
    ): Flow<CoinageTransferDetection> {
        return startTransferDetecting(transferCoins, coinKeys, pastDetection)
            .takeWhileInclusive { !it.isTerminal() }
    }

    private suspend fun startTransferDetecting(
        transferCoins: Boolean,
        coinKeys: List<CoinPrivateKey>,
        pastDetection: CoinageTransferDetection?
    ): Flow<CoinageTransferDetection> {
        val keyPairs = coinKeys.map { it.deriveKeypair() }

        return flow {
            emit(pastDetection ?: CoinageTransferDetection.Detecting)

            when {
                pastDetection == null -> startDetecting(keyPairs, transferCoins)
                pastDetection is CoinageTransferDetection.Detected ->
                    proceedDetected(keyPairs, transferCoins, pastDetection.amount)
            }
        }
    }

    context(FlowCollector<CoinageTransferDetection>)
    private suspend fun startDetecting(keyPairs: List<Keypair>, transferCoins: Boolean) {
        val detectedCoins = awaitCoinsInfo(keyPairs).getOrDefault(mapOf())
        val valueExponents = detectedCoins.map { ValueExponent(it.value.value) }
        val actualDetected = coinageBalanceConverterUseCase.create()
            .map { it.formatExponentsToBalance(valueExponents) }
            .getOrDefault(Balance.ZERO)

        if (actualDetected.isZero()) {
            coinageLogE("Transfer detection failed: no coins detected")
            emit(CoinageTransferDetection.Error.Detection)
        } else {
            coinageLogD("Transfer detected: amount=$actualDetected, coins=${detectedCoins.size}")
            emit(CoinageTransferDetection.Detected(actualDetected))

            if (transferCoins) {
                coinageLogD("Submitting Transfer for ${detectedCoins.size} coins")
                coinageTransferSubmissionUseCase(keyPairs, detectedCoins)
                    .onFailure {
                        emit(CoinageTransferDetection.Error.Transfer)
                        coinageLogE("Transfer tx submission failed", it)
                    }
            }

            proceedDetected(keyPairs, transferCoins, actualDetected)
        }
    }

    context(FlowCollector<CoinageTransferDetection>)
    private suspend fun proceedDetected(keyPairs: List<Keypair>, transferCoins: Boolean, detected: Balance) {
        coinageLogD("Awaiting coins disappear (transferCoins=$transferCoins)")
        awaitCoinsDisappear(keyPairs, transferCoins)
            .onSuccess {
                coinageLogD("Coins disappeared - transfer confirmed on chain")
                emit(CoinageTransferDetection.Transferred(detected))
            }
            .onFailure {
                coinageLogE("Transfer on chain failed", it)
                emit(CoinageTransferDetection.Error.Transfer)
            }
    }

    private suspend fun awaitCoinsInfo(keyPairs: List<Keypair>): Result<Map<AccountId, OnChainCoinInfo>> {
        return runCatching {
            val accountIds = keyPairs.map { it.publicKey.toDataByteArray() }
            var coinsInfo = emptyMap<AccountId, OnChainCoinInfo>()

            val result = withTimeoutOrNull(DETECTION_TIMEOUT) {
                subscribeCoinInfos(accountIds)
                    .runningFold(emptyMap<AccountId, OnChainCoinInfo>()) { acc, current ->
                        acc + current.filterNotNull()
                    }
                    .onEach { coinsInfo = it }
                    .first { it.size == accountIds.size }
            }

            if (result == null) coinageLogE("Transfer detection on chain timed out")
            result ?: coinsInfo
        }
    }

    private suspend fun awaitCoinsDisappear(keyPairs: List<Keypair>, withTimeout: Boolean): Result<Unit> {
        val accountIds = keyPairs.map { it.publicKey.substrateAccountId() }

        return if (withTimeout) {
            runCatching {
                withTimeout(DETECTION_TIMEOUT) {
                    awaitCoinInfosEmpty(accountIds)
                }
            }
        } else {
            awaitCoinInfosEmpty(accountIds)
            Result.success(Unit)
        }
    }

    private suspend fun subscribeCoinInfos(accountIds: List<AccountId>) =
        coinRepository.subscribeCoinsInfoFor(chainAssetProvider.chainId(), accountIds)
            .map { it.logFailure("Can't fetch info for coins").getOrEmpty() }

    private suspend fun awaitCoinInfosEmpty(accountIds: List<AccountId>) =
        subscribeCoinInfos(accountIds)
            .first { it.size == accountIds.size && it.filterNotNull().isEmpty() || it.isEmpty() }
    // it.isEmpty() is here since emptyMap can be returned instead of map with null values
}
