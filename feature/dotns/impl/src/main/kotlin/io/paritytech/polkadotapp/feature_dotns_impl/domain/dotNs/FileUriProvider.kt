package io.paritytech.polkadotapp.feature_dotns_impl.domain.dotNs

import android.net.Uri
import java.io.File
import javax.inject.Inject

fun interface FileUriProvider {
    fun fromFile(file: File): Uri
}

class RealFileUriProvider @Inject constructor() : FileUriProvider {
    override fun fromFile(file: File): Uri = Uri.fromFile(file)
}
