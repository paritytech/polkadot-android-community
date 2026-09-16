package io.paritytech.polkadotapp.common.utils.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.common.presentation.AppInitializer
import io.paritytech.polkadotapp.common.presentation.AppLifecycleObserver
import io.paritytech.polkadotapp.common.presentation.subscribeIsForeground
import io.paritytech.polkadotapp.common.utils.awaitTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

interface NetworkStateService {
    val isNetworkAvailable: StateFlow<Boolean>
}

suspend fun NetworkStateService.awaitNetworkAvailable() = isNetworkAvailable.awaitTrue()

suspend fun <T> NetworkStateService.withNetworkRetries(compute: suspend () -> T) =
    runCatching { compute() }
        .recoverCatching {
            if (isNetworkAvailable.value) throw it

            Timber.d("Failed to perform compute due to internet connectivity. Waiting for network to be available...")

            awaitNetworkAvailable()

            Timber.d("Network is available. Retrying...")

            compute()
        }

// An AppInitializer too: the system may deliver no connectivity callback at all across a sleep, so a
// value left stale at "offline" would otherwise survive the unlock.
@Singleton
@SuppressLint("MissingPermission")
class RealNetworkStateService @Inject constructor(
    @ApplicationContext context: Context,
    private val appLifecycleObserver: AppLifecycleObserver,
) : NetworkStateService, AppInitializer {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _isNetworkAvailable = MutableStateFlow(isNetworkAvailable())
    override val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    context(scope: ComputationalScope)
    override fun initialize(): Result<Unit> {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(
            networkRequest,
            object : ConnectivityManager.NetworkCallback() {
                // Callbacks fire per-Network; recompute from the system's active network so losing one
                // transport while another stays up can't strand the flag at false.
                override fun onAvailable(network: Network) = recompute()

                override fun onLost(network: Network) = recompute()

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) = recompute()
            })

        appLifecycleObserver.subscribeIsForeground()
            .filter { inForeground -> inForeground }
            .onEach { recompute() }
            .launchIn(scope)

        return Result.success(Unit)
    }

    private fun recompute() {
        _isNetworkAvailable.value = isNetworkAvailable()
    }

    private fun isNetworkAvailable(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
