package io.paritytech.polkadotapp.feature_chats_impl.data.hop.upload

import io.novasama.substrate_sdk_android.extensions.fromHex
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.NODE_A
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.NODE_B
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.NODE_C
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.THREE_CHUNK_FILE_SIZE
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.chunkSlices
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.patternBytes
import io.paritytech.polkadotapp.feature_chats_impl.domain.hop.FileUpload
import io.paritytech.polkadotapp.test_shared.assertFailureOf
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
    fun `a retry interrupted by a killed process keeps its node when it was left in progress`() = runBlocking<Unit> {
        withInterruptedRetryOnNodeB()

        uploads.upload(interruptedRetryOnNodeB())

        uploads.verifySessionOpenedOn(NODE_B)
        uploads.verifyNeverAskedForAnotherNode()
    }

    @Test
    fun `a retry interrupted by a killed process resumes its chunks when it was left in progress`() = runBlocking<Unit> {
        withInterruptedRetryOnNodeB()

        uploads.upload(interruptedRetryOnNodeB())

        uploads.verifyStreamedFrom(1)
    }

    @Test
    fun `a first retry keeps its node when it failed once`() = runBlocking<Unit> {
        withRetryOnNodeA()

        uploads.upload(retryOnNodeA(attempts = 1))

        uploads.verifySessionOpenedOn(NODE_A)
        uploads.verifyNeverAskedForAnotherNode()
    }

    @Test
    fun `a first retry resumes its chunks when it failed once`() = runBlocking<Unit> {
        withRetryOnNodeA()

        uploads.upload(retryOnNodeA(attempts = 1))

        uploads.verifyStreamedFrom(1)
    }

    @Test
    fun `a retry keeps the node it moved to when it failed once there`() = runBlocking<Unit> {
        withRetryOnNodeA()

        uploads.upload(retryOnNodeA(attempts = REPEATED_FAILURES + 1))

        uploads.verifySessionOpenedOn(NODE_A)
        uploads.verifyNeverAskedForAnotherNode()
    }

    @Test
    fun `a retry moves again when it failed twice on the node it moved to`() = runBlocking<Unit> {
        uploads.withSuccessfulSession()
        uploads.withFileBytes(INLINE_FILE_SIZE)
        uploads.withRetryNode(failed = NODE_B, picked = NODE_C)

        uploads.upload(retryAfterRepeatedFailuresOn(NODE_B, attempts = 2 * REPEATED_FAILURES))

        uploads.verifySessionOpenedOn(NODE_C)
        uploads.verifyMovedTo(NODE_C)
    }

    @Test
    fun `a retry moves to another node when it failed twice`() = runBlocking<Unit> {
        uploads.withSuccessfulSession()
        uploads.withFileBytes(INLINE_FILE_SIZE)
        uploads.withRetryNode(failed = NODE_B, picked = NODE_C)

        uploads.upload(retryAfterRepeatedFailuresOn(NODE_B))

        uploads.verifySessionOpenedOn(NODE_C)
        uploads.verifyMovedTo(NODE_C)
    }

    @Test
    fun `a retry opens its session on the picked node when another node is available`() = runBlocking<Unit> {
        withRetryFromNodeAToNodeB()

        uploads.upload(retryAfterRepeatedFailuresOn(NODE_A))

        uploads.verifySessionOpenedOn(NODE_B)
        uploads.verifySessionNeverOpenedOn(NODE_A)
    }

    @Test
    fun `a retry stores the picked node when another node is available`() = runBlocking<Unit> {
        withRetryFromNodeAToNodeB()

        uploads.upload(retryAfterRepeatedFailuresOn(NODE_A))

        uploads.verifyMovedTo(NODE_B)
    }

    @Test
    fun `an upload opens its session on the stored node when it is a first attempt`() = runBlocking<Unit> {
        withRetryFromNodeAToNodeB()

        uploads.upload(fileUpload(uploads.fileUri, nodeUrl = NODE_A, attemptCount = 0))

        uploads.verifySessionOpenedOn(NODE_A)
    }

    @Test
    fun `an upload never asks for another node when it is a first attempt`() = runBlocking<Unit> {
        withRetryFromNodeAToNodeB()

        uploads.upload(fileUpload(uploads.fileUri, nodeUrl = NODE_A, attemptCount = 0))

        uploads.verifyNeverAskedForAnotherNode()
    }

    @Test
    fun `a retry keeps its node when that node is the only one`() = runBlocking<Unit> {
        withRetryOnTheOnlyNode()

        uploads.upload(retryOnTheOnlyNode())

        uploads.verifySessionOpenedOn(NODE_A)
        uploads.verifyNeverMovedToAnotherNode()
    }

    @Test
    fun `a retry resumes its chunks when that node is the only one`() = runBlocking<Unit> {
        withRetryOnTheOnlyNode()

        uploads.upload(retryOnTheOnlyNode())

        uploads.verifyStreamedFrom(1)
        assertEquals(3, uploads.submitCount)
    }

    @Test
    fun `a retry lists its stored and new hashes when that node is the only one`() = runBlocking<Unit> {
        withRetryOnTheOnlyNode()

        uploads.upload(retryOnTheOnlyNode())

        uploads.assertRootListsHashes(listOf(STORED_HASH_0.fromHex(), uploads.returnedHash(0), uploads.returnedHash(1)))
    }

    @Test
    fun `an upload submits every chunk again when it moves to a new node`() = runBlocking<Unit> {
        withMoveToNewNode()

        uploads.upload(movedUploadWithStaleHashes())

        uploads.verifyStreamedFrom(0)
        assertEquals(4, uploads.submitCount)
    }

    @Test
    fun `an upload counts its progress from the first chunk when it moves to a new node`() = runBlocking<Unit> {
        withMoveToNewNode()

        uploads.upload(movedUploadWithStaleHashes())

        assertEquals(1, uploads.firstRecordedProgress())
    }

    @Test
    fun `an upload lists only the new hashes when it moves to a new node`() = runBlocking<Unit> {
        withMoveToNewNode()

        uploads.upload(movedUploadWithStaleHashes())

        uploads.assertRootListsHashes(List(3) { uploads.returnedHash(it) })
    }

    @Test
    fun `the message names the node the upload finished on when the upload was rotated`() = runBlocking<Unit> {
        withRetryFromNodeAToNodeB()

        uploads.upload(retryAfterRepeatedFailuresOn(NODE_A))

        assertEquals(NODE_B, uploads.sentAttachment().nodeUrl)
    }

    @Test
    fun `the message names the stored node when the upload is a first attempt`() = runBlocking<Unit> {
        uploads.withSuccessfulSession()
        uploads.withFileBytes(INLINE_FILE_SIZE)

        uploads.upload(fileUpload(uploads.fileUri, nodeUrl = NODE_A, attemptCount = 0))

        assertEquals(NODE_A, uploads.sentAttachment().nodeUrl)
    }

    @Test
    fun `a retry fails before opening a session when no node is available`() = runBlocking<Unit> {
        uploads.withFileBytes(INLINE_FILE_SIZE)
        uploads.withNoRetryNode(failed = NODE_A)

        val result = runCatching {
            uploads.upload(retryAfterRepeatedFailuresOn(NODE_A))
        }

        assertFailureOf<IllegalStateException>(result)
        uploads.verifyNoSessionOpened()
    }

    private suspend fun withInterruptedRetryOnNodeB() {
        uploads.withSuccessfulSession()
        uploads.withUploadStillActive()
        uploads.withRetryNode(failed = NODE_B, picked = NODE_C)
        uploads.withFileSize(THREE_CHUNK_FILE_SIZE)
        uploads.withVideoChunks(fromChunk = 0, patternBytes(THREE_CHUNK_FILE_SIZE).chunkSlices())
        uploads.withVideoChunks(fromChunk = 1, patternBytes(THREE_CHUNK_FILE_SIZE).chunkSlices().drop(1))
    }

    private suspend fun withRetryOnNodeA() {
        uploads.withSuccessfulSession()
        uploads.withUploadStillActive()
        uploads.withRetryNode(failed = NODE_A, picked = NODE_B)
        uploads.withFileSize(THREE_CHUNK_FILE_SIZE)
        uploads.withVideoChunks(fromChunk = 0, patternBytes(THREE_CHUNK_FILE_SIZE).chunkSlices())
        uploads.withVideoChunks(fromChunk = 1, patternBytes(THREE_CHUNK_FILE_SIZE).chunkSlices().drop(1))
    }

    private fun retryOnNodeA(attempts: Int) = fileUpload(
        uri = uploads.fileUri,
        mimeType = VIDEO_MP4,
        nodeUrl = NODE_A,
        attemptCount = attempts,
        status = FileUpload.Status.PENDING,
        uploadedChunkHashes = listOf(STORED_HASH_0)
    )

    private fun retryAfterRepeatedFailuresOn(nodeUrl: String, attempts: Int = REPEATED_FAILURES) = fileUpload(
        uri = uploads.fileUri,
        nodeUrl = nodeUrl,
        attemptCount = attempts,
        status = FileUpload.Status.PENDING
    )

    private fun interruptedRetryOnNodeB() = fileUpload(
        uri = uploads.fileUri,
        mimeType = VIDEO_MP4,
        nodeUrl = NODE_B,
        attemptCount = REPEATED_FAILURES,
        status = FileUpload.Status.IN_PROGRESS,
        uploadedChunkHashes = listOf(STORED_HASH_0)
    )

    private suspend fun withRetryFromNodeAToNodeB() {
        uploads.withSuccessfulSession()
        uploads.withFileBytes(INLINE_FILE_SIZE)
        uploads.withRetryNode(failed = NODE_A, picked = NODE_B)
    }

    private suspend fun withRetryOnTheOnlyNode() {
        uploads.withSuccessfulSession()
        uploads.withUploadStillActive()
        uploads.withRetryNode(failed = NODE_A, picked = NODE_A)
        uploads.withFileSize(THREE_CHUNK_FILE_SIZE)
        uploads.withVideoChunks(fromChunk = 1, patternBytes(THREE_CHUNK_FILE_SIZE).chunkSlices().drop(1))
    }

    private fun retryOnTheOnlyNode() = fileUpload(
        uri = uploads.fileUri,
        mimeType = VIDEO_MP4,
        nodeUrl = NODE_A,
        attemptCount = REPEATED_FAILURES,
        status = FileUpload.Status.PENDING,
        uploadedChunkHashes = listOf(STORED_HASH_0)
    )

    private suspend fun withMoveToNewNode() {
        uploads.withSuccessfulSession()
        uploads.withUploadStillActive()
        uploads.withRetryNode(failed = NODE_A, picked = NODE_B)
        uploads.withFileSize(THREE_CHUNK_FILE_SIZE)
        uploads.withVideoChunks(fromChunk = 0, patternBytes(THREE_CHUNK_FILE_SIZE).chunkSlices())
    }

    private fun movedUploadWithStaleHashes() = fileUpload(
        uri = uploads.fileUri,
        mimeType = VIDEO_MP4,
        nodeUrl = NODE_A,
        attemptCount = REPEATED_FAILURES,
        status = FileUpload.Status.PENDING,
        uploadedChunkHashes = listOf(STALE_HASH_0, STALE_HASH_1)
    )

    private companion object {
        const val REPEATED_FAILURES = 2
    }
}
