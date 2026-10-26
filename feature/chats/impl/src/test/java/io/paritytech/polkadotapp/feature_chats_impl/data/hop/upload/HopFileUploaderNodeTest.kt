package io.paritytech.polkadotapp.feature_chats_impl.data.hop.upload

import io.novasama.substrate_sdk_android.extensions.fromHex
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.NODE_B
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.THREE_CHUNK_FILE_SIZE
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.chunkSlices
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.patternBytes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class HopFileUploaderNodeTest {
    private val uploads = MockedSessionUploads()

    @Before
    fun setUp() = runBlocking<Unit> {
        uploads.withUploadAllowed()
    }

    @Test
    fun `an upload interrupted after its second failure resumes on its stored node`() = runBlocking<Unit> {
        withThreeChunkFile()

        uploads.upload(interruptedAfterRepeatedFailures())

        uploads.verifySessionOpenedOn(NODE_B)
    }

    @Test
    fun `an upload interrupted after its second failure resumes its stored chunks`() = runBlocking<Unit> {
        withThreeChunkFile()

        uploads.upload(interruptedAfterRepeatedFailures())

        uploads.verifyStreamedFrom(1)
        assertEquals(3, uploads.submitCount)
    }

    @Test
    fun `an upload lists its stored and new hashes when it resumes its stored chunks`() = runBlocking<Unit> {
        withThreeChunkFile()

        uploads.upload(interruptedAfterRepeatedFailures())

        uploads.assertRootListsHashes(listOf(STORED_HASH_0.fromHex(), uploads.returnedHash(0), uploads.returnedHash(1)))
    }

    @Test
    fun `the message names the stored node when the upload resumes`() = runBlocking<Unit> {
        withThreeChunkFile()

        uploads.upload(interruptedAfterRepeatedFailures())

        assertEquals(NODE_B, uploads.sentAttachment().nodeUrl)
    }

    @Test
    fun `an upload submits every chunk when it has no stored chunks`() = runBlocking<Unit> {
        withThreeChunkFile()

        uploads.upload(movedAfterRepeatedFailures())

        uploads.verifyStreamedFrom(0)
        assertEquals(4, uploads.submitCount)
    }

    @Test
    fun `an upload counts its progress from the first chunk when it has no stored chunks`() = runBlocking<Unit> {
        withThreeChunkFile()

        uploads.upload(movedAfterRepeatedFailures())

        assertEquals(1, uploads.firstRecordedProgress())
    }

    private suspend fun withThreeChunkFile() {
        uploads.withSuccessfulSession()
        uploads.withUploadStillActive()
        uploads.withFileSize(THREE_CHUNK_FILE_SIZE)
        uploads.withVideoChunks(fromChunk = 0, patternBytes(THREE_CHUNK_FILE_SIZE).chunkSlices())
        uploads.withVideoChunks(fromChunk = 1, patternBytes(THREE_CHUNK_FILE_SIZE).chunkSlices().drop(1))
    }

    private fun interruptedAfterRepeatedFailures() = fileUpload(
        uri = uploads.fileUri,
        mimeType = VIDEO_MP4,
        nodeUrl = NODE_B,
        attemptCount = REPEATED_FAILURES,
        uploadedChunkHashes = listOf(STORED_HASH_0)
    )

    private fun movedAfterRepeatedFailures() = fileUpload(
        uri = uploads.fileUri,
        mimeType = VIDEO_MP4,
        nodeUrl = NODE_B,
        attemptCount = REPEATED_FAILURES
    )

    private companion object {
        const val REPEATED_FAILURES = 2
    }
}
