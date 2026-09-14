package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.components.digitalDollar.holdings

/** Where a voucher row's solid bar and barber pole end up, in pixels. */
internal class VoucherBarLayout(
    val solidWidth: Float,
    val poleLeft: Float,
    val poleWidth: Float,
)

/**
 * Fits a voucher's two marks into one column.
 *
 * The solid bar runs from the left edge to `solidEnd` and the pole ends at `poleEnd`, both fractions of
 * [columnWidth]. Each is floored at [minWidth] so a score that rounds to nothing still leaves a mark, and
 * **neither ever disappears**: a voucher always has both a ceiling and a gap still to close, even when both
 * round away.
 *
 * When the pole would push past the column it is pinned to the right edge and the solid bar gives up the
 * room, because the pole is the reading that keeps changing and so the one that must stay legible. What the
 * pole may never do is take the solid bar's minimum with it.
 * That branch is not an edge case — a voucher whose maximum has not been frozen yet scores zero, which makes
 * the solid bar span the whole column and leaves the pole nowhere to sit.
 *
 * Both marks stay visible for any column wider than `2 * minWidth + gap`, which the details column always is.
 */
internal fun voucherBarLayout(
    columnWidth: Float,
    minWidth: Float,
    gap: Float,
    solidEnd: Float,
    poleEnd: Float,
): VoucherBarLayout {
    // The pole's *minimum* is what takes precedence, not its nominal length: it grows only as far as still
    // leaves the solid bar its own minimum. The outer bound keeps that minimum inviolable even in a column
    // too narrow for both, which the details column never is.
    val poleCeiling = maxOf(minWidth, columnWidth - minWidth - gap)
    val poleWidth = ((poleEnd - solidEnd) * columnWidth).coerceIn(minWidth, poleCeiling)
    val solidNominal = (solidEnd * columnWidth).coerceAtLeast(minWidth)

    val poleLeft = (solidNominal + gap).coerceAtMost(columnWidth - poleWidth)
    val solidWidth = minOf(solidNominal, poleLeft - gap).coerceAtLeast(0f)

    return VoucherBarLayout(solidWidth = solidWidth, poleLeft = poleLeft, poleWidth = poleWidth)
}
