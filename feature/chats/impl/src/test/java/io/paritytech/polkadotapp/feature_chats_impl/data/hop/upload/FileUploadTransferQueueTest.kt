package io.paritytech.polkadotapp.feature_chats_impl.data.hop.upload

import io.paritytech.polkadotapp.feature_chats_impl.data.repository.FileUploadRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class FileUploadTransferQueueTest {
    @Test
    fun `the upload queue allows ten uploads in flight when it is created`() {
        val queue = FileUploadTransferQueue(
            repository = mock(FileUploadRepository::class.java),
            uploader = mock(HopFileUploader::class.java)
        )

        assertEquals(EXPECTED_MAX_CONCURRENT_UPLOADS, queue.maxInFlight)
    }

    private companion object {
        const val EXPECTED_MAX_CONCURRENT_UPLOADS = 10
    }
}
