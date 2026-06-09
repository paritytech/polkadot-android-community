package io.paritytech.polkadotapp.tools_car_parser

import com.google.protobuf.CodedInputStream
import io.paritytech.polkadotapp.tools_ipfs_api.Cid
import java.io.ByteArrayInputStream

/**
 * Parses CARv1 (Content Addressable aRchive) files from IPFS.
 *
 * A CAR file is a binary archive that serializes content-addressed IPLD blocks
 * into a single sequential stream. It is the standard format for transferring
 * IPFS data as a single file (e.g., downloading a website archive from an IPFS gateway).
 *
 * ## Binary layout (CARv1)
 * ```
 * [header-varint | CBOR header] [block-varint | CID | block-data] [block-varint | CID | block-data] ...
 * ```
 * - **Header**: a varint for the byte length, followed by a DAG-CBOR encoded map
 *   containing `{ version: 1, roots: [<root CID>] }`.
 * - **Blocks**: each block is a varint (byte length of CID + data), followed by
 *   a raw CID, followed by the raw block bytes. Blocks are sequential with no index.
 *
 * ## Codecs
 * Each block's CID encodes a codec that determines how to interpret the block data:
 * - **dag-pb** (0x70): DAG-PB protobuf — contains UnixFS file/directory structure.
 * - **raw** (0x55): Raw bytes — the block data is the file content itself, not wrapped in protobuf.
 *
 * ## Reconstruction
 * After decoding, the blocks form a DAG (directed acyclic graph) where each block
 * is addressed by its CID. Starting from the root CID, [UnixFsDecoder] traverses
 * this DAG to reconstruct the original directory/file structure.
 *
 * @see UnixFsDecoder
 * @see CborHeaderDecoder
 */
object CarParser {
    /**
     * Parses a CARv1 archive and reconstructs the file tree.
     */
    fun parse(carBytes: ByteArray): Result<UnpackedCarArchive> = runCatching {
        val carFile = decode(carBytes)

        require(carFile.roots.isNotEmpty()) { "CAR archive has no root CIDs" }
        val rootCid = carFile.roots.first()

        val files = UnixFsDecoder.reconstructFileTree(rootCid, carFile.blocks)
        UnpackedCarArchive(files)
    }

    /**
     * Checks if the byte array starts with a valid CARv1 header.
     * Used to detect legacy deployments where the CID points to an uploaded CAR file
     * rather than a directory.
     */
    fun looksLikeCarArchive(data: ByteArray): Boolean {
        if (data.size < 10) return false

        return try {
            val input = ByteArrayInputStream(data)
            val codedInput = CodedInputStream.newInstance(input)
            val headerLength = codedInput.readRawVarint32()
            if (headerLength <= 0 || headerLength > data.size) return false

            val headerBytes = codedInput.readRawBytes(headerLength)
            val header = CborHeaderDecoder.decode(headerBytes)
            header.version == 1 && header.roots.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private fun decode(carBytes: ByteArray): CarFile {
        val input = ByteArrayInputStream(carBytes)
        val codedInput = CodedInputStream.newInstance(input)

        val headerLength = codedInput.readRawVarint32()
        val headerBytes = codedInput.readRawBytes(headerLength)
        val header = CborHeaderDecoder.decode(headerBytes)

        val blocks = mutableMapOf<Cid, BlockData>()

        while (!codedInput.isAtEnd) {
            val blockLength = codedInput.readRawVarint32()
            val blockBytes = codedInput.readRawBytes(blockLength)

            val blockStream = ByteArrayInputStream(blockBytes)
            val cid = Cid.cast(blockStream as java.io.InputStream)
            val data = blockStream.readBytes()

            blocks[cid] = data
        }

        return CarFile(roots = header.roots, blocks = blocks)
    }

    private class CarFile(
        val roots: List<Cid>,
        val blocks: Map<Cid, BlockData>
    )
}
