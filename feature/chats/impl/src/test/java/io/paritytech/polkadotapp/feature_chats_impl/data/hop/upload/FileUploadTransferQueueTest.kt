package io.paritytech.polkadotapp.feature_chats_impl.data.hop.upload

import io.paritytech.polkadotapp.feature_chats_impl.data.repository.FileUploadRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class FileUploadTransferQueueTest {
    private val uploadConcurrency: UploadConcurrency = mock(UploadConcurrency::class.java)

    @Test
    fun `the upload queue allows the uploads in flight that UploadConcurrency gives when it is created`() {
        withUploadSlots(uploadConcurrency, ALLOWED_UPLOADS)

        val queue = FileUploadTransferQueue(
            repository = mock(FileUploadRepository::class.java),
            uploader = mock(HopFileUploader::class.java),
            uploadConcurrency = uploadConcurrency
        )

        assertEquals(ALLOWED_UPLOADS, queue.maxInFlight)
    }

    private companion object {
        const val ALLOWED_UPLOADS = 6
    }
}
