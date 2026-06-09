package io.paritytech.polkadotapp.feature_products_impl.presentation.spaBrowser

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SpaBrowserPayload(
    val url: String,
) : Parcelable
