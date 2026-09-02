package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.components.digitalDollar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.paritytech.polkadotapp.common.utils.CurrencyConfig
import io.paritytech.polkadotapp.design.components.icon.NovaIcon
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.Refreshing
import io.paritytech.polkadotapp.design.components.spacer.HorizontalSpacer
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.LocalTokenAmountFormatter
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.TokenAmountFormatter
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.formatFiat
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.RoundPrecision
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.TokenAmountModel
import io.paritytech.polkadotapp.feature_wallet_impl.R
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.animation.LocalCardTilt
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.animation.MotionShineParameters
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.animation.maskedMotionShine
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.components.CardSizes
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.components.PocketCardColors
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.components.icons.DigitalDollarIcon
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.pocketBalanceSharedElement
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.models.PocketCardUiModel
import io.paritytech.polkadotapp.common.R as RCommon

@Composable
fun DigitalDollarCard(
    modifier: Modifier = Modifier,
    card: PocketCardUiModel.DigitalDollar,
    onSelected: ((PocketCardUiModel.DigitalDollar) -> Unit)? = null,
    isExpanded: Boolean
) {
    val tiltState = LocalCardTilt.current

    val borderBrush = remember {
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to PocketCardColors.Primary,
                1f to PocketCardColors.Transparent
            ),
            center = Offset.Zero,
            radius = 900f
        )
    }

    val highlightBrush = remember {
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to PocketCardColors.Primary.copy(alpha = 0.35f),
                0.5f to PocketCardColors.Primary.copy(alpha = 0.1f),
                1f to PocketCardColors.Transparent
            ),
            center = Offset.Zero,
            radius = 700f
        )
    }

    PolkadotSurface(
        modifier = modifier,
        shape = PolkadotTheme.shapes.large,
        color = PocketCardColors.DigitalDollarCardBackground,
        border = BorderStroke(Dp.Hairline, borderBrush),
        onClick = { onSelected?.invoke(card) },
        enabled = onSelected != null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CardSizes.HEIGHT)
        ) {
            Image(
                modifier = Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd)
                    .maskedMotionShine(
                        tiltState = tiltState,
                        parameters = MotionShineParameters.DigitalDollarCard,
                        contentAlpha = 0f
                    ),
                painter = painterResource(R.drawable.img_digital_dollar_card),
                contentDescription = null,
                contentScale = ContentScale.FillHeight
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(highlightBrush)
        )

        Image(
            modifier = Modifier.matchParentSize(),
            painter = painterResource(R.drawable.img_texture_grain_dark),
            contentDescription = null,
            contentScale = ContentScale.Crop
        )

        val totalBalanceText = LocalTokenAmountFormatter.current
            .formatTokenAmount(
                tokenAmount = card.balance,
                precision = RoundPrecision.FIAT,
                withSymbol = false
            )

        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(PolkadotTheme.spacings.mediumIncreased)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NovaIcon(
                        imageVector = DigitalDollarIcon,
                        tint = PocketCardColors.Primary
                    )

                    HorizontalSpacer { extraSmall }

                    NovaText(
                        text = stringResource(RCommon.string.pocket_digital_dollar_card_title, CurrencyConfig.symbol),
                        style = PolkadotTheme.typography.title.large,
                        color = PocketCardColors.Primary
                    )
                }

                NovaText(
                    modifier = if (isExpanded) Modifier.alpha(0f) else Modifier.pocketBalanceSharedElement(card.id),
                    text = totalBalanceText,
                    style = PolkadotTheme.typography.headline.medium,
                    color = PocketCardColors.Primary
                )
            }

            if (isExpanded) {
                Column(
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    val balanceStatus = when {
                        card.syncInProgress -> BalanceStatus.Syncing
                        card.notFullyAvailable -> BalanceStatus.Available
                        else -> BalanceStatus.Hidden
                    }

                    AnimatedContent(
                        targetState = balanceStatus,
                        label = "DigitalDollarBalanceStatus"
                    ) { status ->
                        when (status) {
                            BalanceStatus.Syncing -> SyncProgress()
                            BalanceStatus.Available -> AvailableBalance(amount = card.available)

                            BalanceStatus.Hidden -> Unit
                        }
                    }

                    NovaText(
                        modifier = Modifier.pocketBalanceSharedElement(card.id),
                        text = totalBalanceText,
                        style = PolkadotTheme.typography.headline.medium,
                        color = PocketCardColors.Primary
                    )
                }
            }
        }
    }
}

@Composable
fun AvailableBalance(amount: TokenAmountModel) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        NovaText(
            text = LocalTokenAmountFormatter.current.formatFiat(amount),
            style = PolkadotTheme.typography.body.medium,
            color = PocketCardColors.Primary
        )

        HorizontalSpacer { small }

        NovaText(
            text = stringResource(RCommon.string.pocket_digital_dollar_available),
            style = PolkadotTheme.typography.body.medium,
            color = PocketCardColors.Secondary
        )
    }
}

@Composable
private fun SyncProgress() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val infiniteTransition = rememberInfiniteTransition()
        val angle by infiniteTransition.animateFloat(
            initialValue = 0F,
            targetValue = -360F,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing)
            )
        )
        NovaIcon(
            modifier = Modifier
                .size(18.dp)
                .rotate(angle),
            imageVector = NovaIcons.Refreshing,
            tint = PocketCardColors.Primary
        )

        HorizontalSpacer { tiny }

        NovaText(
            text = stringResource(RCommon.string.asset_details_backup_in_progress),
            style = PolkadotTheme.typography.body.medium,
            color = PocketCardColors.Primary
        )
    }
}

private enum class BalanceStatus {
    Syncing,
    Available,
    Hidden
}

@Preview
@Composable
private fun DigitalDollarCardPreview() {
    PolkadotTheme {
        CompositionLocalProvider(
            LocalTokenAmountFormatter provides TokenAmountFormatter.mocked
        ) {
            DigitalDollarCard(
                card = PocketCardUiModel.DigitalDollar(TokenAmountModel.mock, TokenAmountModel.mock, true),
                isExpanded = true
            )
        }
    }
}
