package io.paritytech.polkadotapp.feature_chats_impl.data.hop.download

import io.novasama.substrate_sdk_android.encrypt.keypair.substrate.Sr25519Keypair
import io.novasama.substrate_sdk_android.extensions.fromHex
import io.novasama.substrate_sdk_android.extensions.toHexString
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.paritytech.polkadotapp.chains.util.sign
import io.paritytech.polkadotapp.chains.util.signing.MessageSigningContext
import io.paritytech.polkadotapp.common.data.storage.file.FileProvider
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.ChatMessageAttachmentUpdater
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.HopNodeUrlProvider
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.HopService
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.HopSigningPayloads
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.encryption.HopEncryption
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.encryption.HopTicketKeyDerivation
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.model.HopMultiSignature
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.model.HopUploadedFile
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.FileDownloadRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.storage.AttachmentFileStorage
import io.paritytech.polkadotapp.feature_chats_impl.domain.hop.FileDownload
import kotlinx.serialization.decodeFromByteArray
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class HopFileDownloader @Inject constructor(
    private val hopService: HopService,
    private val hopNodeUrlProvider: HopNodeUrlProvider,
    private val ticketKeyDerivation: HopTicketKeyDerivation,
    private val attachmentFileStorage: AttachmentFileStorage,
    private val fileDownloadRepository: FileDownloadRepository,
    private val messageAttachmentUpdater: ChatMessageAttachmentUpdater,
    private val fileProvider: FileProvider
) {
    suspend fun download(download: FileDownload) {
        check(hopNodeUrlProvider.isAllowed(download.nodeUrl)) {
            "Node url ${download.nodeUrl} is not in the known Hop node allowlist — refusing to download"
        }

        hopService.withSession(download.nodeUrl) {
            val encryption = HopEncryption(ticketKeyDerivation.deriveEncryptionKey(download.ticket))
            val signingKeyPair = ticketKeyDerivation.deriveSigningKeyPair(download.ticket)

            val chunkHashes = resolveChunkHashes(download, signingKeyPair, encryption)
            val file = resolveFile(download)

            downloadChunks(download, file, chunkHashes, signingKeyPair, encryption)

            updateMessage(download, file)
        }
    }

    private suspend fun HopService.Session.resolveChunkHashes(
        download: FileDownload,
        signingKeyPair: Sr25519Keypair,
        encryption: HopEncryption
    ): List<ByteArray> {
        when (val metadata = download.progress.metadata) {
            is FileDownload.Metadata.Resolved -> return metadata.chunkHashes.map { it.fromHex() }
            is FileDownload.Metadata.Pending -> {
                val claimedMetadata = claimMetadata(download.identifier.value, signingKeyPair, encryption)

                fileDownloadRepository.saveMetadata(
                    messageId = download.messageId,
                    chunkHashes = claimedMetadata.chunksHashes.map { it.toHexString(withPrefix = true) }
                )

                return claimedMetadata.chunksHashes
            }
        }
    }

    private fun resolveFile(download: FileDownload): File {
        download.filePath?.let { return File(it) }

        return attachmentFileStorage.createDownloadFile(download.mimeType)
    }

    private suspend fun HopService.Session.downloadChunks(
        download: FileDownload,
        file: File,
        chunkHashes: List<ByteArray>,
        signingKeyPair: Sr25519Keypair,
        encryption: HopEncryption
    ) {
        val startFrom = download.progress.downloadedChunks
        val remaining = chunkHashes.drop(startFrom)
        val appendFile = startFrom > 0

        FileOutputStream(file, appendFile).use { output ->
            for ((index, chunkHash) in remaining.withIndex()) {
                val encryptedChunk = claimBlobAndAck(chunkHash, signingKeyPair)
                val decryptedChunk = encryption.decrypt(encryptedChunk)

                output.write(decryptedChunk)

                fileDownloadRepository.updateProgress(
                    messageId = download.messageId,
                    downloadedChunks = startFrom + index + 1,
                    filePath = file.absolutePath
                )
            }
        }
    }

    private suspend fun updateMessage(download: FileDownload, file: File) {
        messageAttachmentUpdater.updateReceivedAttachment(
            chatId = download.chatId,
            messageId = download.messageId,
            identifier = download.identifier,
            uri = fileProvider.uriOf(file)
        )
    }

    private suspend fun HopService.Session.claimMetadata(
        identifier: ByteArray,
        signingKeyPair: Sr25519Keypair,
        encryption: HopEncryption
    ): HopUploadedFile {
        val encryptedMetadata = claimBlobAndAck(identifier, signingKeyPair)
        val metadataBytes = encryption.decrypt(encryptedMetadata)

        return BinaryScale.decodeFromByteArray(metadataBytes)
    }

    private suspend fun HopService.Session.claimBlobAndAck(
        hash: ByteArray,
        signingKeyPair: Sr25519Keypair
    ): ByteArray {
        val claimSignature = HopMultiSignature.SR25519(
            signingKeyPair.sign(HopSigningPayloads.claim(hash), MessageSigningContext.trustedContent())
        )
        val data = claim(hash, claimSignature)

        val ackSignature = HopMultiSignature.SR25519(
            signingKeyPair.sign(HopSigningPayloads.ack(hash), MessageSigningContext.trustedContent())
        )
        runCatching { ack(hash, ackSignature) }
            .onFailure { Timber.w(it, "hop_ack failed for ${hash.toHexString(withPrefix = true)}; treating as terminal") }

        return data
    }
}
