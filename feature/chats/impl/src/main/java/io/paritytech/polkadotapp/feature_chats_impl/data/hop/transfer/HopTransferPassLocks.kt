package io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HopTransferPassLocks @Inject constructor() {
    private val queueing = Mutex()
    private val passes = HopTransfer.entries.associateWith { Mutex() }

    suspend fun <T> runPass(transfer: HopTransfer, pass: suspend () -> T): T {
        return passes.getValue(transfer).withLock { pass() }
    }

    suspend fun whileQueueing(action: suspend () -> Unit) {
        queueing.withLock { action() }
    }
}
