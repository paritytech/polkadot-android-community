package io.paritytech.polkadotapp.feature_chats_impl.data.hop.upload

import io.novasama.substrate_sdk_android.extensions.fromHex
import io.novasama.substrate_sdk_android.extensions.toHexString
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.InformationSize
import io.paritytech.polkadotapp.common.utils.InformationSize.Companion.bytes
import io.paritytech.polkadotapp.common.utils.blake2b256
import io.paritytech.polkadotapp.common.utils.chunked
import io.paritytech.polkadotapp.feature_chats_api.domain.model.Attachment
import io.paritytech.polkadotapp.feature_chats_impl.data.AttachmentMetaBuilder
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.ChatMessageAttachmentUpdater
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.HopService
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.HopSigningPayloads
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.auth.HopSigner
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.encryption.HopEncryption
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.encryption.HopTicketKeyDerivation
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.model.HopMultiSigner
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.model.HopUploadedFile
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.FileUploadRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.storage.AttachmentFileStorage
import io.paritytech.polkadotapp.feature_chats_impl.domain.hop.FileUpload
import kotlinx.serialization.encodeToByteArray
import javax.inject.Inject
import kotlin.math.ceil

class HopFileUploader @Inject constructor(
    private val attachmentFileStorage: AttachmentFileStorage,
    private val hopService: HopService,
    private val hopSigner: HopSigner,
    private val ticketKeyDerivation: HopTicketKeyDerivation,
    private val fileUploadRepository: FileUploadRepository,
    private val messageAttachmentUpdater: ChatMessageAttachmentUpdater,
    private val attachmentMetaBuilder: AttachmentMetaBuilder,
    private val preProcessors: Set<@JvmSuppressWildcards FileUploadPreProcessor>
) {
    suspend fun upload(upload: FileUpload) {
        hopService.withSession(upload.nodeUrl) {
            val submitterSigner = hopSigner.multiSigner()

            val encryption = HopEncryption(ticketKeyDerivation.deriveEncryptionKey(upload.ticket))
            val signingKeyPair = ticketKeyDerivation.deriveSigningKeyPair(upload.ticket)
            val recipient = HopMultiSigner.SR25519(signingKeyPair.publicKey)

            val raw = attachmentFileStorage.readFileBytes(upload.meta.uri)
            val fileBytes = preProcessors.fold(raw) { bytes, processor -> processor.preProcess(bytes, upload.meta.mimeType) }

            val fileSize = fileBytes.size.toLong().bytes
            val totalChunks = determineNumberOfChunks(fileSize)
            fileUploadRepository.updateFileInfo(upload.messageId, fileSize, totalChunks)

            val chunkHashes = uploadChunks(upload, fileBytes, encryption, recipient, submitterSigner)
            val metadataHash = submitMetadata(fileBytes, chunkHashes, encryption, recipient, submitterSigner)

            val attachmentMeta = attachmentMetaBuilder.build(
                uri = upload.meta.uri,
                mimeType = upload.meta.mimeType,
                size = fileSize
            )

            updateMessage(upload, metadataHash, attachmentMeta)
        }
    }

    private suspend fun HopService.Session.uploadChunks(
        upload: FileUpload,
        fileBytes: ByteArray,
        encryption: HopEncryption,
        recipient: HopMultiSigner,
        submitterSigner: HopMultiSigner
    ): List<ByteArray> {
        val previousHashes = upload.progress.uploadedChunkHashes.map { it.fromHex() }
        val allChunkHashes = previousHashes.toMutableList()

        val chunks = fileBytes.chunked(CHUNK_SIZE_BYTES)

        for (i in upload.progress.uploadedChunks until chunks.size) {
            val chunkHash = submitChunk(chunks[i], encryption, recipient, submitterSigner)
            allChunkHashes.add(chunkHash)

            fileUploadRepository.updateProgress(
                messageId = upload.messageId,
                uploadedChunks = i + 1,
                chunkHashes = allChunkHashes.map { it.toHexString(withPrefix = true) }
            )
        }

        return allChunkHashes
    }

    private suspend fun HopService.Session.submitChunk(
        chunk: ByteArray,
        encryption: HopEncryption,
        recipient: HopMultiSigner,
        submitterSigner: HopMultiSigner
    ): ByteArray {
        val encryptedChunk = encryption.encrypt(chunk)
        submitToHop(encryptedChunk, recipient, submitterSigner)
        return encryptedChunk.blake2b256()
    }

    private suspend fun HopService.Session.submitMetadata(
        fileBytes: ByteArray,
        chunkHashes: List<ByteArray>,
        encryption: HopEncryption,
        recipient: HopMultiSigner,
        submitterSigner: HopMultiSigner
    ): ByteArray {
        val metadata = HopUploadedFile(
            totalSize = fileBytes.size.toULong(),
            chunksHashes = chunkHashes
        )
        val encodedMetadata = BinaryScale.encodeToByteArray(metadata)
        val encryptedMetadata = encryption.encrypt(encodedMetadata)

        submitToHop(encryptedMetadata, recipient, submitterSigner)

        return encryptedMetadata.blake2b256()
    }

    private suspend fun HopService.Session.submitToHop(
        encryptedData: ByteArray,
        recipient: HopMultiSigner,
        submitterSigner: HopMultiSigner
    ) {
        val timestamp = System.currentTimeMillis()
        val payload = HopSigningPayloads.submit(encryptedData, timestamp)
        val signature = hopSigner.sign(payload)
        submit(
            data = encryptedData,
            recipients = listOf(recipient),
            signature = signature,
            signer = submitterSigner,
            submitTimestampMs = timestamp
        )
    }

    private suspend fun updateMessage(upload: FileUpload, metadataHash: ByteArray, meta: Attachment.Meta) {
        val attachment = Attachment.Hosted(
            uri = upload.meta.uri,
            identifier = metadataHash.toDataByteArray(),
            ticket = upload.ticket,
            nodeUrl = upload.nodeUrl,
            meta = meta
        )

        messageAttachmentUpdater.updateSentAttachment(
            chatId = upload.chatId,
            messageId = upload.messageId,
            attachment = attachment
        )
    }

    companion object {
        private const val CHUNK_SIZE_BYTES = 2000000

        fun determineNumberOfChunks(fileSize: InformationSize): Int {
            return ceil(fileSize.inWholeBytes.toDouble() / CHUNK_SIZE_BYTES).toInt()
        }
    }
}
