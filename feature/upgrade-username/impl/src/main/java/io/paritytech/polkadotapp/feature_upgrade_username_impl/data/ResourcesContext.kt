package io.paritytech.polkadotapp.feature_upgrade_username_impl.data

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext

val BandersnatchContext.Companion.RESOURCES: BandersnatchContext
    get() = BandersnatchContext.fromString("pop:polkadot.network/resources  ")
