package io.paritytech.polkadotapp.feature_dotns_impl.domain.tld

import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTld
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTldProvider
import io.paritytech.polkadotapp.feature_dotns_impl.data.repository.NetworkSuffixRepository
import io.paritytech.polkadotapp.feature_dotns_impl.data.storage.DotNsTldStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Singleton
internal class RealDotNsTldProvider @Inject constructor(
    private val networkSuffixRepository: NetworkSuffixRepository,
    private val tldStorage: DotNsTldStorage,
    private val dispatchers: CoroutineDispatchers
) : DotNsTldProvider, CoroutineScope {
    override val coroutineContext = dispatchers.io + SupervisorJob()

    private val fetchMutex = Mutex()
    private val persistedTld by lazy { tldStorage.getTld() }
    private val settledTld = MutableStateFlow<DotNsTld?>(null)

    override fun currentTldOrNull(): DotNsTld? {
        settledTld.value?.let { return it }
        kickRefresh()
        return persistedTld
    }

    override suspend fun getTld(): Result<DotNsTld> {
        settledTld.value?.let { return Result.success(it) }

        return fetchMutex.withLock { fetchAndSettle() }
    }

    private fun kickRefresh() {
        if (fetchMutex.isLocked) return

        launch {
            getTld()
        }
    }

    private suspend fun fetchAndSettle(): Result<DotNsTld> {
        settledTld.value?.let { return Result.success(it) }

        return readNetworkSuffix()
            .mapCatching(::requireReported)
            .onSuccess(::settle)
            .logFailure("Failed to read the network suffix from the people chain")
    }

    private suspend fun readNetworkSuffix(): Result<DotNsTld?> {
        return withTimeoutOrNull(NETWORK_SUFFIX_READ_LIMIT) { networkSuffixRepository.networkSuffix() }
            ?: Result.failure(TimeoutException("No network suffix answer within $NETWORK_SUFFIX_READ_LIMIT"))
    }

    // Deriving keys under a guessed namespace mints material that belongs to no network, so a
    // missing suffix stays unsettled instead of standing in for a real answer.
    private fun requireReported(tld: DotNsTld?): DotNsTld {
        return requireNotNull(tld) { "People chain reported no usable network suffix" }
    }

    private fun settle(tld: DotNsTld) {
        settledTld.value = tld
        tldStorage.putTld(tld)
    }

    private companion object {
        val NETWORK_SUFFIX_READ_LIMIT = 30.seconds
    }
}
