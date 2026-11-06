package io.paritytech.polkadotapp.feature_device_sync_impl.domain.fileTransfer

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.decodeFromByteArray
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.encodeToByteArray
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.InformationSize.Companion.bytes
import io.paritytech.polkadotapp.feature_device_sync_api.domain.SiblingFile
import io.paritytech.polkadotapp.feature_device_sync_api.domain.SiblingFileOutcome
import io.paritytech.polkadotapp.feature_device_sync_api.domain.SiblingFileRequest
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.FileAvailabilityScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.FileReferenceScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.FileUnavailableReasonScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.FilesAvailabilityRequestScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.MessageAttachmentIdScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncFileMessageScale
import io.paritytech.polkadotapp.test_shared.settle
import io.paritytech.polkadotapp.tools_media_connection_api.domain.DataTransport
import io.paritytech.polkadotapp.tools_media_connection_api.domain.DataTransportState
import io.paritytech.polkadotapp.tools_media_connection_api.domain.UseCaseData
import io.paritytech.polkadotapp.tools_media_connection_api.domain.UseCaseId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SyncFileTransferTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val phone = LoopbackTransport()
    private val desktop = LoopbackTransport()
    private val phoneRegistry = SyncFileChannelRegistry()
    private val fetcher = RealSiblingFileFetcher(phoneRegistry)

    init {
        phone.peer = desktop
        desktop.peer = phone
    }

    @Test
    fun `file spanning several chunks is fetched from the sibling`() = runBlocking<Unit> {
        val content = Random.nextBytes(150_000)
        runPhone()
        runDesktop(mapOf("m1" to stored(content)))

        val destination = destination("m1")
        val outcomes = fetcher.fetch(listOf(request("m1", destination)))

        assertEquals(listOf(SiblingFileOutcome.Completed), outcomes)
        assertArrayEquals(content, destination.readBytes())
        assertFalse(partialOf(destination).exists())
        coroutineContext.cancelChildren()
    }

    @Test
    fun `interrupted file resumes from the stored bytes`() = runBlocking<Unit> {
        val content = Random.nextBytes(150_000)
        val destination = destination("m1")
        partialOf(destination).apply {
            parentFile?.mkdirs()
            writeBytes(content.copyOf(70_000))
        }
        runPhone()
        runDesktop(mapOf("m1" to stored(content)))

        val outcomes = fetcher.fetch(listOf(request("m1", destination)))

        assertEquals(listOf(SiblingFileOutcome.Completed), outcomes)
        assertArrayEquals(content, destination.readBytes())
        coroutineContext.cancelChildren()
    }

    @Test
    fun `every file of a request gets its own outcome in order`() = runBlocking<Unit> {
        val content = Random.nextBytes(10)
        runPhone()
        runDesktop(
            mapOf(
                "stored" to stored(content),
                "downloading" to SyncFileLocation.Unavailable(FileUnavailableReasonScale.DOWNLOADING),
            )
        )

        val outcomes = fetcher.fetch(
            listOf(
                request("downloading", destination("downloading")),
                request("stored", destination("stored")),
                request("missing", destination("missing")),
            )
        )

        val expected = listOf(
            SiblingFileOutcome.Unavailable(SiblingFileOutcome.Unavailable.Reason.DOWNLOADING),
            SiblingFileOutcome.Completed,
            SiblingFileOutcome.Unavailable(SiblingFileOutcome.Unavailable.Reason.NOT_STORED),
        )
        assertEquals(expected, outcomes)
        assertArrayEquals(content, destination("stored").readBytes())
        coroutineContext.cancelChildren()
    }

    @Test
    fun `without an open channel files are not stored`() = runBlocking<Unit> {
        val outcomes = fetcher.fetch(listOf(request("m1", destination("m1"))))

        assertEquals(listOf(SiblingFileOutcome.Unavailable(SiblingFileOutcome.Unavailable.Reason.NOT_STORED)), outcomes)
    }

    @Test
    fun `silent sibling is treated as not storing the files`() = runBlocking<Unit> {
        runPhone()

        val outcomes = fetcher.fetch(listOf(request("m1", destination("m1"))))

        assertEquals(listOf(SiblingFileOutcome.Unavailable(SiblingFileOutcome.Unavailable.Reason.NOT_STORED)), outcomes)
        coroutineContext.cancelChildren()
    }

    @Test
    fun `closing the channel interrupts the request in flight`() = runBlocking<Unit> {
        val phoneTransfer = runPhone(noResponseTimeout = 10.seconds)

        val outcomes = async { fetcher.fetch(listOf(request("m1", destination("m1")))) }
        settle()
        phoneTransfer.cancel()

        assertEquals(listOf(SiblingFileOutcome.Interrupted), outcomes.await())
        assertEquals(null, phoneRegistry.openRequester())
    }

    @Test
    fun `availability request is answered with sizes and reasons`() = runBlocking<Unit> {
        runDesktop(mapOf("m1" to stored(Random.nextBytes(300))))
        val response = async {
            phone.subscribeMessages(SYNC_FILE_USE_CASE)
                .mapNotNull { BinaryScale.decodeFromByteArray<SyncFileMessageScale>(it) as? SyncFileMessageScale.AvailabilityResponse }
                .first()
        }
        settle()

        val request = FilesAvailabilityRequestScale(requestId = 3u, files = listOf(reference("m1"), reference("missing")))
        phone.send(SYNC_FILE_USE_CASE, BinaryScale.encodeToByteArray<SyncFileMessageScale>(SyncFileMessageScale.AvailabilityRequest(request)))

        val availability = response.await().response.availability
        assertEquals(300uL, (availability[0] as FileAvailabilityScale.Available).totalSize)
        assertEquals(FileUnavailableReasonScale.NOT_STORED, (availability[1] as FileAvailabilityScale.Unavailable).reason)
        coroutineContext.cancelChildren()
    }

    private suspend fun CoroutineScope.runPhone(noResponseTimeout: Duration = 200.milliseconds) =
        launch { SyncFileTransfer(phone, FakeLocator(emptyMap()), phoneRegistry, "desktop", noResponseTimeout).run() }
            .also { settle() }

    private suspend fun CoroutineScope.runDesktop(files: Map<String, SyncFileLocation>) =
        launch { SyncFileTransfer(desktop, FakeLocator(files), SyncFileChannelRegistry(), "phone", 200.milliseconds).run() }
            .also { settle() }

    private fun stored(content: ByteArray) = SyncFileLocation.Stored(content.size.bytes) { ByteArrayInputStream(content) }

    private fun request(messageId: String, destination: File) = SiblingFileRequest(
        file = SiblingFile.MessageAttachment(messageId, identifier = byteArrayOf(1).toDataByteArray()),
        destination = destination,
    )

    private fun reference(messageId: String): FileReferenceScale =
        FileReferenceScale.MessageAttachment(MessageAttachmentIdScale(messageId, identifier = byteArrayOf(1)))

    private fun destination(messageId: String) = File(folder.root, "downloads/$messageId.bin")

    private fun partialOf(destination: File) = File(destination.path + SyncFileWriter.PARTIAL_SUFFIX)

    private class FakeLocator(private val files: Map<String, SyncFileLocation>) : SyncFileLocator {
        override suspend fun locate(file: FileReferenceScale): SyncFileLocation {
            val messageId = (file as FileReferenceScale.MessageAttachment).attachment.messageId
            return files[messageId] ?: SyncFileLocation.Unavailable(FileUnavailableReasonScale.NOT_STORED)
        }
    }

    private class LoopbackTransport : DataTransport {
        lateinit var peer: LoopbackTransport

        // Replays to late subscribers, so a test doesn't race the transfer subscribing.
        private val received = MutableSharedFlow<Pair<UseCaseId, UseCaseData>>(replay = Int.MAX_VALUE)

        override val state: StateFlow<DataTransportState> = MutableStateFlow(DataTransportState.Open)

        override fun subscribeMessages(id: UseCaseId): Flow<UseCaseData> =
            received.filter { it.first == id }.map { it.second }

        override suspend fun send(id: UseCaseId, data: UseCaseData) {
            peer.received.emit(id to data)
        }

        override suspend fun awaitOpen() = Unit

        override fun isOpen(): Boolean = true
    }

    private companion object {
        const val SYNC_FILE_USE_CASE = "device-sync-file"
    }
}
