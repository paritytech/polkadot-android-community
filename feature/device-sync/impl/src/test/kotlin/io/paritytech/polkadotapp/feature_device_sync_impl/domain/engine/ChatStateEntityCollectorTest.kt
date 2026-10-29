package io.paritytech.polkadotapp.feature_device_sync_impl.domain.engine

import io.novasama.substrate_sdk_android.extensions.toHexString
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.encodeToByteArray
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.ChatStateSyncUseCase
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatStateForSync
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncEntityScale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Instant

class ChatStateEntityCollectorTest {
    private val timestamp = 1_700_000_000_000L
    private val timestampHex = "0068e5cf8b010000"

    @Test
    fun `chat state becomes wire entities in the order the receiver applies them`() = runTest {
        val state = ChatStateForSync(
            blockStates = listOf(ChatStateForSync.BlockState(account(0x01), isBlocked = true)),
            processedIncomingRequests = listOf(ChatStateForSync.IncomingRequest("i1", account(0x02), timestamp, isAccepted = false)),
            pendingOutgoingRequests = listOf(
                ChatStateForSync.OutgoingRequest("o1", account(0x05), timestamp, encodedContent = byteArrayOf(0, 0, 0))
            ),
            outgoingRequestUpdates = listOf(ChatStateForSync.OutgoingRequestUpdate("o2", isCancelled = true)),
        )

        val entities = ChatStateEntityCollector(FakeChatStateSyncUseCase(state)).collect(Instant.fromEpochMilliseconds(500))

        assertEquals(
            listOf(
                "07" + "04" + "086f31" + "05".repeat(32) + timestampHex + "00" + "000000",
                "04" + "04" + "00" + "01".repeat(32) + "01",
                "05" + "04" + "086931" + "02".repeat(32) + timestampHex + "02" + "00" + "00" + "00",
                "08" + "04" + "086f32" + "02",
            ),
            entities.map { BinaryScale.encodeToByteArray<SyncEntityScale>(it).toHexString() }
        )
    }

    @Test
    fun `nothing synced yet asks for the full state, a later checkpoint for changes after it`() = runTest {
        val useCase = FakeChatStateSyncUseCase(ChatStateForSync(emptyList(), emptyList(), emptyList(), emptyList()))
        val collector = ChatStateEntityCollector(useCase)

        collector.collect(Instant.fromEpochMilliseconds(0))
        collector.collect(Instant.fromEpochMilliseconds(500))

        assertEquals(listOf(null, Instant.fromEpochMilliseconds(500)), useCase.requestedChangedAfter)
    }

    private fun account(seed: Byte) = ByteArray(32) { seed }.toDataByteArray()

    private class FakeChatStateSyncUseCase(private val state: ChatStateForSync) : ChatStateSyncUseCase {
        val requestedChangedAfter = mutableListOf<Instant?>()

        override suspend fun getChatState(changedAfter: Instant?): ChatStateForSync {
            requestedChangedAfter += changedAfter
            return state
        }

        override fun observeChatStateChanges(): Flow<Unit> = emptyFlow()
    }
}
