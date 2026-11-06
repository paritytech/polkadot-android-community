package io.paritytech.polkadotapp.feature_device_sync_impl.domain.fileTransfer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncFileChannelRegistry @Inject constructor() {
    class Registration(val requester: SyncFileRequester)

    private val channels = MutableStateFlow<List<Registration>>(emptyList())

    fun register(requester: SyncFileRequester): Registration {
        val registration = Registration(requester)
        channels.update { it + registration }
        return registration
    }

    fun unregister(registration: Registration) {
        channels.update { it - registration }
    }

    fun openRequester(): SyncFileRequester? = channels.value.lastOrNull()?.requester
}
