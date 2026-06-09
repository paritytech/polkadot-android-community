package io.paritytech.polkadotapp.feature_dotns_api.domain

import android.net.Uri
import java.io.File

interface DotNsResolver {
    suspend fun resolveToLocalUri(dotNsName: String): Result<Uri>

    suspend fun getMetadataEntry(dotNsName: String, key: String): Result<String?>

    suspend fun clearCache()
}

suspend fun DotNsResolver.resolveToLocalFile(dotNsName: String): Result<File> {
    return resolveToLocalUri(dotNsName).map { File(it.path!!) }
}
