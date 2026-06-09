package io.paritytech.polkadotapp.tools_car_parser

import io.paritytech.polkadotapp.common.utils.buildByteArray
import io.paritytech.polkadotapp.tools_car_parser.proto.UnixFsProto
import io.paritytech.polkadotapp.tools_ipfs_api.Cid

typealias FilePath = String

/**
 * Decodes UnixFS protobuf data and reconstructs file trees from IPLD block maps.
 *
 * UnixFS is a protobuf schema (see `unixfs.proto`) embedded inside DAG-PB nodes
 * that gives file system semantics (files, directories, symlinks) to the raw
 * linked DAG structure.
 *
 * ## Block codecs
 * Each block's CID carries a codec:
 * - **dag-pb** (0x70): DAG-PB protobuf with embedded UnixFS — parsed for structure.
 * - **raw** (0x55): Raw bytes — the block data is the file content itself.
 *
 * ## File tree reconstruction algorithm
 *
 * Starting from the root CID, the decoder performs a DFS traversal:
 *
 * 1. **Look up** the block by CID in the block map.
 * 2. **Check codec**: if raw, emit the data directly as file content.
 * 3. **Decode DAG-PB** to get the embedded UnixFS data and child links.
 * 4. **Branch by UnixFS type**:
 *    - *Directory*: recurse into each link, appending the link name to the path.
 *    - *Leaf*: emit the inline content at the current path.
 *    - *ChunkedFile*: follow the links in order, concatenating each child's data
 *      to reassemble the original file. Children may themselves be chunked (nested).
 *
 * @see DagPbDecoder
 */
object UnixFsDecoder {
    fun reconstructFileTree(
        rootCid: Cid,
        blocks: Map<Cid, BlockData>
    ): Map<FilePath, FileContent> {
        val result = mutableMapOf<FilePath, FileContent>()
        traverseNode(rootCid, "", blocks, result)
        return result
    }

    private fun traverseNode(
        cid: Cid,
        currentPath: FilePath,
        blocks: Map<Cid, BlockData>,
        result: MutableMap<FilePath, FileContent>
    ) {
        val blockData = blocks[cid]
            ?: throw IllegalStateException("Block not found for CID: $cid")

        // Raw codec blocks contain file content directly, not DAG-PB protobuf
        if (cid.codec == Cid.Codec.Raw) {
            result[currentPath] = blockData
            return
        }

        val dagPbNode = DagPbDecoder.decode(blockData)

        require(dagPbNode.data != null || dagPbNode.links.isNotEmpty()) {
            "Empty DAG-PB node (no data, no links) at path: $currentPath"
        }

        val unixFs = dagPbNode.data?.let { decodeUnixFs(it) }

        when (unixFs) {
            // A node with no UnixFS data but with links is treated as a directory.
            // This is valid in IPFS — some directory nodes omit the UnixFS wrapper.
            is UnixFsData.Directory, null -> {
                for (link in dagPbNode.links) {
                    val linkName = requireNotNull(link.name) { "Directory entry is missing a name at path: $currentPath" }
                    traverseNode(link.hash, "$currentPath/$linkName", blocks, result)
                }
            }
            is UnixFsData.Leaf -> {
                result[currentPath] = unixFs.content
            }
            is UnixFsData.ChunkedFile -> {
                result[currentPath] = assembleChunkedFile(dagPbNode, blocks)
            }
        }
    }

    /**
     * Reassembles a chunked file by following DAG-PB links in order.
     * Each link points to a child block that is either a leaf chunk (raw or DAG-PB)
     * or another level of chunking (for very large files).
     */
    private fun assembleChunkedFile(
        dagPbNode: DagPbNode,
        blocks: Map<Cid, BlockData>
    ): FileContent = buildByteArray {
        for (link in dagPbNode.links) {
            val block = blocks[link.hash]
                ?: throw IllegalStateException("Chunk block not found for CID: ${link.hash}")

            // Raw codec blocks are leaf chunks — use data directly
            if (link.hash.codec == Cid.Codec.Raw) {
                write(block)
                continue
            }

            val childDagPb = DagPbDecoder.decode(block)
            val childUnixFs = childDagPb.data?.let { decodeUnixFs(it) }

            when {
                childDagPb.links.isNotEmpty() -> write(assembleChunkedFile(childDagPb, blocks))
                childUnixFs is UnixFsData.Leaf -> write(childUnixFs.content)
                else -> error("Unexpected chunk child: expected Leaf or nested chunks, got $childUnixFs")
            }
        }
    }

    private fun decodeUnixFs(data: BlockData): UnixFsData {
        val proto = UnixFsProto.Data.parseFrom(data)

        return when (proto.type) {
            UnixFsProto.Data.DataType.Directory -> UnixFsData.Directory

            UnixFsProto.Data.DataType.Raw -> {
                require(proto.hasData()) { "UnixFS Raw node is missing data" }
                UnixFsData.Leaf(content = proto.data.toByteArray())
            }

            else -> {
                val isChunked = proto.blocksizesCount > 0
                if (isChunked) {
                    UnixFsData.ChunkedFile
                } else {
                    require(proto.hasData()) { "UnixFS File leaf node is missing data" }
                    UnixFsData.Leaf(content = proto.data.toByteArray())
                }
            }
        }
    }
}

private sealed interface UnixFsData {
    data object Directory : UnixFsData

    data object ChunkedFile : UnixFsData

    class Leaf(val content: FileContent) : UnixFsData
}
