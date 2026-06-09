package io.paritytech.polkadotapp.feature_coinage_api.domain.model

import io.paritytech.polkadotapp.common.domain.model.AccountId

typealias DerivationIndex = Int

data class Coin(
    val derivationIndex: DerivationIndex,
    val valueExponent: ValueExponent,
    val age: Age,
    val spentState: SpentState,
    val accountId: AccountId
) {
    sealed interface Age {
        data object Unknown : Age
        data class Known(val value: Int) : Age
    }

    enum class SpentState {
        SPENT_LOCALLY,
        SPENT_ON_CHAIN,
        NOT_SPENT
    }
}

fun Coin.knownAgeOrThrow() = age as Coin.Age.Known

fun Coin.tokenAmount() = valueExponent.tokenAmount()

fun Coin.isAgeValidToSpend(recyclableAge: Int) = when (age) {
    is Coin.Age.Known -> age.value < recyclableAge
    Coin.Age.Unknown -> false
}

fun Coin.ageOrDefault() = (this.age as? Coin.Age.Known)?.value ?: -1

fun Coin.canBeSpent(recyclableAge: Int) = !isSpent() && isAgeValidToSpend(recyclableAge)

fun List<Coin>.filterSpendable(recyclableAge: Int): List<Coin> {
    return filter { it.canBeSpent(recyclableAge) }
}

fun Coin.notSpent() = !isSpent()

fun Coin.isSpent() = when (spentState) {
    Coin.SpentState.SPENT_LOCALLY,
    Coin.SpentState.SPENT_ON_CHAIN -> true

    Coin.SpentState.NOT_SPENT -> false
}
