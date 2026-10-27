package io.paritytech.polkadotapp.feature_chats_impl.data.hop.compaction

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer.HopTransferPassLocks
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer.enqueueQuietTransfer
import javax.inject.Inject

class CompactionExpansionStarter @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val passLocks: HopTransferPassLocks
) {
    suspend fun startExpansion(): Result<Unit> = enqueueQuietTransfer<CompactionExpansionWorker>(appContext, CompactionExpansionWorker.WORK_ID, passLocks)
}
