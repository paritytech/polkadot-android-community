package io.paritytech.polkadotapp.feature_dotns_impl.data.storage

import io.paritytech.polkadotapp.common.utils.HexString
import io.paritytech.polkadotapp.tools_car_parser.FileContent
import io.paritytech.polkadotapp.tools_car_parser.FilePath
import java.io.File

typealias ContentHash = HexString

interface DotNsContentStorage {
    suspend fun saveContent(contentHash: ContentHash, files: Map<FilePath, FileContent>)

    fun getContentDirectory(contentHash: ContentHash): File?

    fun contentExists(contentHash: ContentHash): Boolean

    suspend fun deleteContent(contentHash: ContentHash)

    suspend fun deleteAll()
}
