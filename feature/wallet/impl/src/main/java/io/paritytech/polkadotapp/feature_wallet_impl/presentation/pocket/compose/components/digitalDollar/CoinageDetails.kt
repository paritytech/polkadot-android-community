package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.components.digitalDollar

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.paritytech.polkadotapp.design.components.button.default.PolkadotTextButton
import io.paritytech.polkadotapp.design.components.icon.NovaIcon
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.ContentCopy
import io.paritytech.polkadotapp.design.components.spacer.FillerSpacer
import io.paritytech.polkadotapp.design.components.spacer.HorizontalSpacer
import io.paritytech.polkadotapp.design.components.spacer.VerticalSpacer
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.isInRecycler
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.recyclerMembersOrZero
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.tokenAmount
import kotlinx.collections.immutable.ImmutableList
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import io.paritytech.polkadotapp.common.R as RCommon

enum class CoinageDetails { COINS, VOUCHERS }

private enum class CoinSortMode { INDEX_DESC, INDEX_ASC, VALUE_DESC, VALUE_ASC, AGE_DESC, AGE_ASC }

private enum class VoucherSortMode { INDEX_DESC, INDEX_ASC, VALUE_DESC, VALUE_ASC, DATE_DESC, DATE_ASC }

@Composable
internal fun CoinsListSheetContent(
    coins: ImmutableList<Coin>,
    onForceRecycleClick: (Coin) -> Unit
) {
    var sortMode by remember { mutableStateOf(CoinSortMode.INDEX_DESC) }

    val sortedCoins = remember(coins, sortMode) {
        when (sortMode) {
            CoinSortMode.INDEX_DESC -> coins.sortedByDescending { it.derivationIndex }
            CoinSortMode.INDEX_ASC -> coins.sortedBy { it.derivationIndex }
            CoinSortMode.VALUE_DESC -> coins.sortedByDescending { it.valueExponent.value }
            CoinSortMode.VALUE_ASC -> coins.sortedBy { it.valueExponent.value }
            CoinSortMode.AGE_DESC -> coins.sortedByDescending { (it.age as? Coin.Age.Known)?.value ?: -1 }
            CoinSortMode.AGE_ASC -> coins.sortedBy { (it.age as? Coin.Age.Known)?.value ?: Int.MAX_VALUE }
        }
    }

    val indexLabel = when (sortMode) {
        CoinSortMode.INDEX_DESC -> stringResource(RCommon.string.pocket_balance_sort_index_descending)
        CoinSortMode.INDEX_ASC -> stringResource(RCommon.string.pocket_balance_sort_index_ascending)
        else -> stringResource(RCommon.string.pocket_balance_sort_index)
    }
    val valueLabel = when (sortMode) {
        CoinSortMode.VALUE_DESC -> stringResource(RCommon.string.pocket_balance_sort_value_descending)
        CoinSortMode.VALUE_ASC -> stringResource(RCommon.string.pocket_balance_sort_value_ascending)
        else -> stringResource(RCommon.string.pocket_balance_sort_value)
    }
    val ageLabel = when (sortMode) {
        CoinSortMode.AGE_DESC -> stringResource(RCommon.string.pocket_balance_sort_age_descending)
        CoinSortMode.AGE_ASC -> stringResource(RCommon.string.pocket_balance_sort_age_ascending)
        else -> stringResource(RCommon.string.pocket_balance_sort_age)
    }
    val indexActive = sortMode == CoinSortMode.INDEX_DESC || sortMode == CoinSortMode.INDEX_ASC
    val valueActive = sortMode == CoinSortMode.VALUE_DESC || sortMode == CoinSortMode.VALUE_ASC
    val ageActive = sortMode == CoinSortMode.AGE_DESC || sortMode == CoinSortMode.AGE_ASC

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedMessage = stringResource(RCommon.string.pocket_balance_copied)

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            NovaText(
                text = stringResource(RCommon.string.pocket_balance_coins_title, coins.size),
                style = PolkadotTheme.typography.title.large
            )

            VerticalSpacer { small }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PolkadotTheme.spacings.small)
            ) {
                SortChip(
                    label = indexLabel,
                    active = indexActive,
                    onClick = {
                        sortMode = when (sortMode) {
                            CoinSortMode.INDEX_DESC -> CoinSortMode.INDEX_ASC
                            CoinSortMode.INDEX_ASC -> CoinSortMode.INDEX_DESC
                            else -> CoinSortMode.INDEX_DESC
                        }
                    }
                )
                SortChip(
                    label = valueLabel,
                    active = valueActive,
                    onClick = {
                        sortMode = when (sortMode) {
                            CoinSortMode.VALUE_DESC -> CoinSortMode.VALUE_ASC
                            CoinSortMode.VALUE_ASC -> CoinSortMode.VALUE_DESC
                            else -> CoinSortMode.VALUE_DESC
                        }
                    }
                )
                SortChip(
                    label = ageLabel,
                    active = ageActive,
                    onClick = {
                        sortMode = when (sortMode) {
                            CoinSortMode.AGE_DESC -> CoinSortMode.AGE_ASC
                            CoinSortMode.AGE_ASC -> CoinSortMode.AGE_DESC
                            else -> CoinSortMode.AGE_DESC
                        }
                    }
                )
                PolkadotSurface(
                    shape = PolkadotTheme.shapes.full,
                    color = Color(0x0FFFFFFF),
                    onClick = {
                        clipboardManager.setText(AnnotatedString(coins.toString()))
                        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                    }
                ) {
                    NovaIcon(
                        modifier = Modifier
                            .padding(6.dp)
                            .size(14.dp),
                        imageVector = NovaIcons.ContentCopy
                    )
                }
            }
        }

        if (sortedCoins.isEmpty()) {
            NovaText(
                modifier = Modifier.padding(16.dp),
                text = stringResource(RCommon.string.pocket_balance_coins_empty)
            )
        } else {
            LazyColumn {
                items(sortedCoins) { coin ->
                    CoinItemCard(
                        coin = coin,
                        onForceRecycleClick = { onForceRecycleClick(coin) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun VouchersListSheetContent(
    vouchers: ImmutableList<RecyclerVoucher>
) {
    var sortMode by remember { mutableStateOf(VoucherSortMode.INDEX_DESC) }

    val sortedVouchers = remember(vouchers, sortMode) {
        when (sortMode) {
            VoucherSortMode.INDEX_DESC -> vouchers.sortedByDescending { it.ringVrfKeyIndex }
            VoucherSortMode.INDEX_ASC -> vouchers.sortedBy { it.ringVrfKeyIndex }
            VoucherSortMode.VALUE_DESC -> vouchers.sortedByDescending { it.recyclerValue.value }
            VoucherSortMode.VALUE_ASC -> vouchers.sortedBy { it.recyclerValue.value }
            VoucherSortMode.DATE_DESC -> vouchers.sortedByDescending { it.ringVrfKeyIndex }
            VoucherSortMode.DATE_ASC -> vouchers.sortedBy { it.ringVrfKeyIndex }
        }
    }

    val indexLabel = when (sortMode) {
        VoucherSortMode.INDEX_DESC -> stringResource(RCommon.string.pocket_balance_sort_index_descending)
        VoucherSortMode.INDEX_ASC -> stringResource(RCommon.string.pocket_balance_sort_index_ascending)
        else -> stringResource(RCommon.string.pocket_balance_sort_index)
    }
    val valueLabel = when (sortMode) {
        VoucherSortMode.VALUE_DESC -> stringResource(RCommon.string.pocket_balance_sort_value_descending)
        VoucherSortMode.VALUE_ASC -> stringResource(RCommon.string.pocket_balance_sort_value_ascending)
        else -> stringResource(RCommon.string.pocket_balance_sort_value)
    }
    val dateLabel = when (sortMode) {
        VoucherSortMode.DATE_DESC -> stringResource(RCommon.string.pocket_balance_sort_date_descending)
        VoucherSortMode.DATE_ASC -> stringResource(RCommon.string.pocket_balance_sort_date_ascending)
        else -> stringResource(RCommon.string.pocket_balance_sort_date)
    }
    val indexActive = sortMode == VoucherSortMode.INDEX_DESC || sortMode == VoucherSortMode.INDEX_ASC
    val valueActive = sortMode == VoucherSortMode.VALUE_DESC || sortMode == VoucherSortMode.VALUE_ASC
    val dateActive = sortMode == VoucherSortMode.DATE_DESC || sortMode == VoucherSortMode.DATE_ASC

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedMessage = stringResource(RCommon.string.pocket_balance_copied)

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            NovaText(
                text = stringResource(RCommon.string.pocket_balance_vouchers_title, vouchers.size),
                style = PolkadotTheme.typography.title.large
            )

            VerticalSpacer { small }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PolkadotTheme.spacings.small)
            ) {
                SortChip(
                    label = indexLabel,
                    active = indexActive,
                    onClick = {
                        sortMode = when (sortMode) {
                            VoucherSortMode.INDEX_DESC -> VoucherSortMode.INDEX_ASC
                            VoucherSortMode.INDEX_ASC -> VoucherSortMode.INDEX_DESC
                            else -> VoucherSortMode.INDEX_DESC
                        }
                    }
                )
                SortChip(
                    label = valueLabel,
                    active = valueActive,
                    onClick = {
                        sortMode = when (sortMode) {
                            VoucherSortMode.VALUE_DESC -> VoucherSortMode.VALUE_ASC
                            VoucherSortMode.VALUE_ASC -> VoucherSortMode.VALUE_DESC
                            else -> VoucherSortMode.VALUE_DESC
                        }
                    }
                )
                SortChip(
                    label = dateLabel,
                    active = dateActive,
                    onClick = {
                        sortMode = when (sortMode) {
                            VoucherSortMode.DATE_DESC -> VoucherSortMode.DATE_ASC
                            VoucherSortMode.DATE_ASC -> VoucherSortMode.DATE_DESC
                            else -> VoucherSortMode.DATE_DESC
                        }
                    }
                )
                PolkadotSurface(
                    shape = PolkadotTheme.shapes.full,
                    color = Color(0x0FFFFFFF),
                    onClick = {
                        clipboardManager.setText(AnnotatedString(vouchers.toString()))
                        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                    }
                ) {
                    NovaIcon(
                        modifier = Modifier
                            .padding(6.dp)
                            .size(14.dp),
                        imageVector = NovaIcons.ContentCopy
                    )
                }
            }
        }

        if (sortedVouchers.isEmpty()) {
            NovaText(
                modifier = Modifier.padding(16.dp),
                text = stringResource(RCommon.string.pocket_balance_vouchers_empty)
            )
        } else {
            LazyColumn {
                items(sortedVouchers) { voucher ->
                    VoucherItemCard(voucher = voucher)
                }
            }
        }
    }
}

@Composable
private fun SortChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    PolkadotSurface(
        shape = PolkadotTheme.shapes.small,
        color = if (active) Color(0x1FFFFFFF) else Color(0x0FFFFFFF),
        onClick = onClick
    ) {
        NovaText(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            text = label,
            style = PolkadotTheme.typography.body.small
        )
    }
}

@Composable
private fun CoinItemCard(
    coin: Coin,
    onForceRecycleClick: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedMessage = stringResource(RCommon.string.pocket_balance_copied)
    val fullAddress = coin.accountId.toString()

    PolkadotSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = PolkadotTheme.shapes.medium,
        color = Color(0x0FFFFFFF)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row {
                NovaText(
                    text = stringResource(RCommon.string.pocket_balance_item_index),
                    color = PolkadotTheme.colors.fg.tertiary
                )
                NovaText(
                    text = "${coin.derivationIndex}"
                )
                FillerSpacer()

                val (presenceText, presenceColor) = if (coin.isOnChain) {
                    stringResource(RCommon.string.pocket_balance_item_on_chain) to PolkadotTheme.colors.fg.success
                } else {
                    stringResource(RCommon.string.pocket_balance_item_not_on_chain) to PolkadotTheme.colors.fg.tertiary
                }
                NovaText(text = presenceText, color = presenceColor)
            }

            VerticalSpacer { 4.dp }

            Row {
                NovaText(
                    text = stringResource(RCommon.string.pocket_balance_item_value),
                    color = PolkadotTheme.colors.fg.tertiary
                )
                FillerSpacer()
                val dollars = "$" + String.format(Locale.US, "%.2f", coin.valueExponent.tokenAmount().toDouble() / 100.0)
                NovaText(
                    text = stringResource(
                        RCommon.string.pocket_balance_item_value_format,
                        coin.valueExponent.value,
                        dollars
                    )
                )
            }

            VerticalSpacer { 4.dp }

            Row {
                NovaText(
                    text = stringResource(RCommon.string.pocket_balance_item_age),
                    color = PolkadotTheme.colors.fg.tertiary
                )
                FillerSpacer()
                val ageText = when (val age = coin.age) {
                    Coin.Age.Unknown -> stringResource(RCommon.string.pocket_balance_item_age_unknown)
                    is Coin.Age.Known -> "${age.value}"
                }
                NovaText(text = ageText)
            }

            VerticalSpacer { 4.dp }

            Row(verticalAlignment = Alignment.CenterVertically) {
                NovaText(
                    text = stringResource(RCommon.string.pocket_balance_item_account),
                    color = PolkadotTheme.colors.fg.tertiary
                )
                FillerSpacer()
                IconButton(
                    modifier = Modifier.size(24.dp),
                    onClick = {
                        clipboardManager.setText(AnnotatedString(fullAddress))
                        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                    }
                ) {
                    NovaIcon(
                        modifier = Modifier.size(14.dp),
                        imageVector = NovaIcons.ContentCopy,
                        tint = PolkadotTheme.colors.fg.tertiary
                    )
                }

                HorizontalSpacer { 4.dp }

                NovaText(
                    modifier = Modifier.width(100.dp),
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    text = fullAddress
                )
            }

            if (coin.isOnChain) {
                VerticalSpacer { 8.dp }

                PolkadotTextButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(RCommon.string.pocket_balance_item_force_recycle),
                    onClick = onForceRecycleClick
                )
            }
        }
    }
}

@Composable
private fun VoucherItemCard(voucher: RecyclerVoucher) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedMessage = stringResource(RCommon.string.pocket_balance_copied)
    val isReady = voucher.isInRecycler()
    val ringMembers = voucher.recyclerMembersOrZero()
    val fullKey = voucher.ringVrfPublicKey.toString()

    PolkadotSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = PolkadotTheme.shapes.medium,
        color = Color(0x0FFFFFFF)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row {
                NovaText(
                    text = stringResource(RCommon.string.pocket_balance_item_index),
                    color = PolkadotTheme.colors.fg.tertiary
                )

                NovaText(
                    text = "${voucher.ringVrfKeyIndex}"
                )
                FillerSpacer()
                Column(horizontalAlignment = Alignment.End) {
                    val statusText = if (isReady) {
                        stringResource(RCommon.string.pocket_balance_item_in_recycler, ringMembers)
                    } else {
                        stringResource(RCommon.string.pocket_balance_item_not_in_recycler)
                    }
                    val statusColor = if (isReady) PolkadotTheme.colors.fg.success else PolkadotTheme.colors.fg.tertiary
                    NovaText(
                        text = statusText,
                        color = statusColor
                    )
                }
            }

            VerticalSpacer { 4.dp }

            Row {
                NovaText(
                    text = stringResource(RCommon.string.pocket_balance_item_value),
                    color = PolkadotTheme.colors.fg.tertiary
                )
                FillerSpacer()
                val dollars = "$" + String.format(Locale.US, "%.2f", voucher.recyclerValue.tokenAmount().toDouble() / 100.0)
                NovaText(
                    text = stringResource(
                        RCommon.string.pocket_balance_item_value_format,
                        voucher.recyclerValue.value,
                        dollars
                    )
                )
            }

            VerticalSpacer { 4.dp }

            Row {
                NovaText(
                    text = stringResource(RCommon.string.pocket_balance_item_location),
                    color = PolkadotTheme.colors.fg.tertiary
                )
                FillerSpacer()
                when (val loc = voucher.location) {
                    RecyclerVoucher.Location.Unknown ->
                        NovaText(stringResource(RCommon.string.pocket_balance_item_location_unknown))

                    RecyclerVoucher.Location.Onboarding ->
                        NovaText(stringResource(RCommon.string.pocket_balance_item_location_onboarding))

                    is RecyclerVoucher.Location.InRecycler -> {
                        NovaText(
                            text = stringResource(RCommon.string.pocket_balance_item_location_index),
                            color = PolkadotTheme.colors.fg.tertiary
                        )
                        NovaText(text = "${loc.recyclerIndex}")
                    }
                }
            }

            VerticalSpacer { 4.dp }

            Row {
                NovaText(
                    text = stringResource(RCommon.string.pocket_balance_item_unload_at),
                    color = PolkadotTheme.colors.fg.tertiary
                )
                FillerSpacer()
                NovaText(text = "$ringMembers")
            }

            VerticalSpacer { 4.dp }

            Row(verticalAlignment = Alignment.CenterVertically) {
                NovaText(
                    text = stringResource(RCommon.string.pocket_balance_item_key),
                    color = PolkadotTheme.colors.fg.tertiary
                )
                FillerSpacer()
                IconButton(
                    modifier = Modifier.size(24.dp),
                    onClick = {
                        clipboardManager.setText(AnnotatedString(fullKey))
                        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                    }
                ) {
                    NovaIcon(
                        modifier = Modifier.size(14.dp),
                        imageVector = NovaIcons.ContentCopy,
                        tint = PolkadotTheme.colors.fg.tertiary
                    )
                }
                NovaText(
                    text = fullKey,
                    modifier = Modifier.width(100.dp),
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
        }
    }
}

private fun Long.toDateString(): String {
    return SimpleDateFormat("dd.MM.yy HH:mm", Locale.US).format(Date(this))
}
