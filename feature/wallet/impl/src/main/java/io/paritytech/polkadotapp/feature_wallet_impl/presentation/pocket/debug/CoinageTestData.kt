package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.debug

import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.CoinageBalanceConversionContext
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.balance
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageBalance
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageInstallationId
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageKeyIndex
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerFungibility
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_wallet_impl.domain.model.CoinageHolding
import io.paritytech.polkadotapp.feature_wallet_impl.domain.model.CoinageHoldingsInfo
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.coins.CoinageCoinDesign
import kotlin.random.Random

/**
 * TEMPORARY — TODO: remove the whole `debug` package once the coin depiction has been reviewed.
 *
 * Synthetic holdings for eyeballing the coins against inputs the testnet will not readily produce. Generated
 * as domain holdings so the real ordering, wear ladder and balance partition still run over them, which is
 * what makes the strip comparable with the one a live account draws.
 *
 * Call sites to delete with it:
 *  - `CoinageUiState.testDataMode`
 *  - `DigitalDollarCardDetailsViewModel.testDataMode` / `onTestDataModeSelected`
 *  - the `CoinageTestDataSwitch` block in `CoinageCardContent`
 *
 * Labels are inline rather than in `strings.xml` deliberately: nothing here is meant to survive, and keeping
 * every trace in one package is worth more than the string-extraction rule for throwaway scaffolding.
 */
enum class CoinageTestDataMode(val label: String) {
    NONE("No test data"),
    EMPTY("Nothing held"),
    ONE_PER_DENOMINATION("One coin per denomination, max fungibility"),
    ALL_STATUSES("One value, 20 wear variants"),
    RANDOM_50("50 random holdings"),
    RANDOM_500("500 random holdings")
}

/**
 * Null for [CoinageTestDataMode.NONE], which is the signal to leave the real holdings alone.
 *
 * Takes the same conversion the real holdings get. A denomination is `2^exponent` *minor* units — a coin of
 * exponent zero is one cent, not one dollar — and how many plancks a minor unit is worth is chain data, not
 * anything derivable from the asset's precision. Doing that arithmetic here instead put every figure out by
 * two orders of magnitude while the coin faces, which are struck per exponent, stayed right.
 */
context(conversion: CoinageBalanceConversionContext)
fun CoinageTestDataMode.generateHoldingsInfo(): CoinageHoldingsInfo? {
    val holdings = when (this) {
        CoinageTestDataMode.NONE -> return null
        // The one state the wallet cannot be talked into on a testnet, and the card draws nothing at all in
        // it, so it is worth being able to look at.
        CoinageTestDataMode.EMPTY -> emptyList()
        CoinageTestDataMode.ONE_PER_DENOMINATION -> onePerDenomination(name)
        CoinageTestDataMode.ALL_STATUSES -> allStatuses(name)
        CoinageTestDataMode.RANDOM_50 -> randomHoldings(name, count = 50, seed = 50L)
        CoinageTestDataMode.RANDOM_500 -> randomHoldings(name, count = 500, seed = 500L)
    }

    return CoinageHoldingsInfo(balance = holdings.toBalance(), holdings = holdings)
}

/** Every denomination once, all fully fungible, so the metals, outlines and sizes can be read off in a row. */
private fun onePerDenomination(mode: String): List<CoinageHolding> = DENOMINATIONS.mapIndexed { index, exponent ->
    holding(
        mode = mode,
        item = index,
        exponent = exponent,
        isReady = true,
        fungibility = MAX_PERCENT,
        isBatchUnloaded = false,
        hops = 0
    )
}

/**
 * One denomination, every shape a coin's wear can take: the ladder from untraceable to fully traceable, the
 * batch-unload penalty, a holding with no recycler record at all, and payment histories from one hop to past
 * the eight pits a face can hold.
 */
private fun allStatuses(mode: String): List<CoinageHolding> {
    var item = 0

    fun next(
        fungibility: Int?,
        hops: Int = 0,
        isBatchUnloaded: Boolean = false,
        isReady: Boolean = true
    ) = holding(mode, item++, SAMPLE_DENOMINATION, isReady, fungibility, isBatchUnloaded, hops)

    return listOf(
        // The ladder, most fungible first. Ten levels, sampled at the scores that land on each.
        next(fungibility = 100),
        next(fungibility = 66),
        next(fungibility = 43),
        next(fungibility = 28),
        next(fungibility = 19),
        next(fungibility = 12),
        next(fungibility = 8),
        next(fungibility = 5),
        next(fungibility = 2),
        next(fungibility = 0),
        // No recycler record: wears as though it hides among nobody, whatever it is worth.
        next(fungibility = null),
        // The same score with and without the batch-unload penalty, side by side.
        next(fungibility = 90),
        next(fungibility = 90, isBatchUnloaded = true),
        // Payment histories: one pit each, up to the eight a face holds and past it.
        next(fungibility = 80, hops = 1),
        next(fungibility = 80, hops = 2),
        next(fungibility = 80, hops = 4),
        next(fungibility = 80, hops = 8),
        next(fungibility = 80, hops = 12),
        // Both partitions, so the strip has a gap and two rules under it.
        next(fungibility = 50, isReady = false),
        next(fungibility = 20, hops = 3, isReady = false)
    )
}

private fun randomHoldings(mode: String, count: Int, seed: Long): List<CoinageHolding> {
    val random = Random(seed)

    return List(count) { index ->
        holding(
            mode = mode,
            item = index,
            exponent = DENOMINATIONS.random(random),
            isReady = random.nextBoolean(),
            fungibility = if (random.nextInt(UNKNOWN_HISTORY_IN_N) == 0) null else random.nextInt(MAX_PERCENT + 1),
            isBatchUnloaded = random.nextInt(BATCH_UNLOADED_IN_N) == 0,
            hops = random.nextInt(MAX_RANDOM_HOPS + 1)
        )
    }
}

/**
 * Ids are scoped to the mode. The field keys coins by id so a holding keeps its face and its place across a
 * relayout, and two modes sharing `test-0` made switching between them read as one coin changing value
 * rather than as a new set arriving.
 */
private fun holding(
    mode: String,
    item: Int,
    exponent: Int,
    isReady: Boolean,
    fungibility: Int?,
    isBatchUnloaded: Boolean,
    hops: Int
) = CoinageHolding(
    id = "$mode-$item",
    exponent = ValueExponent(exponent),
    derivationIndex = CoinageKeyIndex(TEST_INSTALLATION, item),
    isReady = isReady,
    recyclerFungibility = fungibility?.let(RecyclerFungibility::ofPercent),
    isBatchUnloaded = isBatchUnloaded,
    hops = hops
)

/**
 * Ready holdings are the available figure and everything else is pending, which is the same split the card
 * draws — so the two figures under the rules agree with the runs above them.
 */
context(conversion: CoinageBalanceConversionContext)
private fun List<CoinageHolding>.toBalance(): CoinageBalance {
    val (ready, clearing) = partition { it.isReady }

    return CoinageBalance(
        availablePrivate = ready.sumOfValues(),
        gainingPrivacy = CoinageBalance.GainingPrivacyBalance(
            amount = Balance.ZERO,
            canSpendWithConfirmation = true
        ),
        pending = clearing.sumOfValues()
    )
}

context(conversion: CoinageBalanceConversionContext)
private fun List<CoinageHolding>.sumOfValues(): Balance =
    fold(Balance.ZERO) { running, holding -> running + holding.exponent.balance() }

private val TEST_INSTALLATION =
    CoinageInstallationId(ByteArray(CoinageInstallationId.SIZE_BYTES).toDataByteArray())

/** The exponents the coin designs cover, which is what the depiction can draw. */
private val DENOMINATIONS = (0..CoinageCoinDesign.HIGHEST_EXPONENT).toList()

private const val SAMPLE_DENOMINATION = 8

private const val MAX_PERCENT = 100
private const val MAX_RANDOM_HOPS = 10
private const val UNKNOWN_HISTORY_IN_N = 8
private const val BATCH_UNLOADED_IN_N = 6
