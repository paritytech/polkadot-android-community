package io.paritytech.polkadotapp.common.utils.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import io.paritytech.polkadotapp.common.utils.awaitTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

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

@SuppressLint("MissingPermission")
class RealNetworkStateService @Inject constructor(@ApplicationContext context: Context) : NetworkStateService {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val networksWithInternet = ConcurrentHashMap.newKeySet<Network>()
    private val _isNetworkAvailable = MutableStateFlow(false)
    override val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    init {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(
            networkRequest,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = publish { networksWithInternet.add(network) }

                // activeNetwork may still name the dying network, and no later callback corrects a stale true.
                override fun onLost(network: Network) = publish { networksWithInternet.remove(network) }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) = publish {
                    if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        networksWithInternet.add(network)
                    } else {
                        networksWithInternet.remove(network)
                    }
                }
            })
    }

    private inline fun publish(update: () -> Unit) {
        update()
        _isNetworkAvailable.value = networksWithInternet.isNotEmpty()
    }
}
