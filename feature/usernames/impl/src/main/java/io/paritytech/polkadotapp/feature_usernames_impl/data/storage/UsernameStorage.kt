package io.paritytech.polkadotapp.feature_usernames_impl.data.storage

import io.paritytech.polkadotapp.common.data.storage.SingleValueStorageFactory
import io.paritytech.polkadotapp.common.data.storage.preferences.store.PreferencesSingleValueSerializer
import io.paritytech.polkadotapp.feature_usernames_api.data.LocalUsernameStorage
import io.paritytech.polkadotapp.feature_usernames_api.domain.model.Username

private const val KEY = "Username"

fun SingleValueStorageFactory.usernameStorage(): LocalUsernameStorage {
    return preferences(
        key = KEY,
        serializer = PreferencesSingleValueSerializer.from(
            toString = Username::getDisplayUsername,
            fromString = Username::fromFullValue
        ),
        default = null
    )
}
