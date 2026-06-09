package io.paritytech.polkadotapp.feature_videogame_impl.data

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext

val BandersnatchContext.Companion.SCORE: BandersnatchContext
    get() = BandersnatchContext.fromString("pop:polkadot.network/score      ")
