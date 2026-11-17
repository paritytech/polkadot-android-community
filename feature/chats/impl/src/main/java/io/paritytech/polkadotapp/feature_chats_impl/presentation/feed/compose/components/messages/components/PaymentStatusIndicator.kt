package io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.compose.components.messages.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.paritytech.polkadotapp.design.components.icon.NovaIcon
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.ArrowUpward
import io.paritytech.polkadotapp.design.components.icon.vectors.Check
import io.paritytech.polkadotapp.design.components.icon.vectors.CheckDouble
import io.paritytech.polkadotapp.design.components.icon.vectors.Retry
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_chats_api.presentation.model.ChatMessageUiModel
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.LocalTokenAmountFormatter
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.formatFiatSigned
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.RoundPrecision
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.TokenAmountModel
import io.paritytech.polkadotapp.common.R as RCommon

@Composable
internal fun PaymentStatusIndicator(
    modifier: Modifier = Modifier,
    status: ChatMessageUiModel.CoinagePayment.Status,
    baseStatusColor: Color,
    isIncoming: Boolean,
    username: String,
    amount: TokenAmountModel,
    differingAmount: TokenAmountModel?
) {
    val formatter = LocalTokenAmountFormatter.current

    val icon: ImageVector?
    val text: String
    val color: Color

    if (differingAmount != null) {
        icon = null
        text = if (isIncoming) {
            stringResource(
                RCommon.string.chat_payment_amount_differs_recipient,
                formatter.formatFiatSigned(differingAmount),
                formatter.formatFiatSigned(amount)
            )
        } else {
            stringResource(
                RCommon.string.chat_payment_amount_differs_sender,
                username,
                formatter.formatFiatSigned(differingAmount),
                formatter.formatFiatSigned(amount)
            )
        }
        color = PolkadotTheme.colors.fg.warning
    } else {
        when (status) {
            is ChatMessageUiModel.CoinagePayment.Status.Detecting -> {
                icon = NovaIcons.ArrowUpward
                text = stringResource(
                    if (isIncoming) {
                        RCommon.string.chat_payment_status_detecting_recipient
                    } else {
                        RCommon.string.chat_payment_status_detecting_sender
                    }
                )
                color = baseStatusColor
            }

            is ChatMessageUiModel.CoinagePayment.Status.Detected,
            is ChatMessageUiModel.CoinagePayment.Status.PartiallyClaimed -> {
                if (isIncoming) {
                    icon = NovaIcons.ArrowUpward
                    text = stringResource(RCommon.string.chat_payment_status_claiming_recipient)
                } else {
                    icon = NovaIcons.Check
                    text = stringResource(RCommon.string.chat_payment_status_sent_sender)
                }
                color = baseStatusColor
            }

            is ChatMessageUiModel.CoinagePayment.Status.Transferred -> {
                icon = NovaIcons.CheckDouble
                text = if (isIncoming) {
                    stringResource(RCommon.string.chat_payment_status_claimed_recipient)
                } else {
                    stringResource(RCommon.string.chat_payment_status_claimed_sender, username)
                }
                color = baseStatusColor
            }

            ChatMessageUiModel.CoinagePayment.Status.FailedDetection,
            ChatMessageUiModel.CoinagePayment.Status.FailedTransfer -> {
                icon = null
                text = stringResource(
                    if (isIncoming) {
                        RCommon.string.chat_payment_status_failed_recipient
                    } else {
                        RCommon.string.chat_payment_status_failed_sender
                    }
                )
                color = PolkadotTheme.colors.fg.warning
            }

            ChatMessageUiModel.CoinagePayment.Status.Unclaimed -> {
                icon = null
                text = stringResource(RCommon.string.chat_payment_status_unclaimed)
                color = PolkadotTheme.colors.fg.warning
            }

            ChatMessageUiModel.CoinagePayment.Status.Reclaiming -> {
                icon = NovaIcons.Retry
                text = stringResource(RCommon.string.chat_payment_status_reclaiming)
                color = baseStatusColor
            }

            is ChatMessageUiModel.CoinagePayment.Status.Reclaimed -> {
                icon = null
                text = if (status.reclaimed.amount.compareTo(amount.amount) == 0) {
                    stringResource(RCommon.string.chat_payment_status_reclaimed)
                } else {
                    stringResource(
                        RCommon.string.chat_payment_status_reclaimed_partially,
                        formatter.formatTokenAmount(status.reclaimed, RoundPrecision.DEFAULT)
                    )
                }
                color = baseStatusColor
            }

            ChatMessageUiModel.CoinagePayment.Status.ReclaimFailed -> {
                icon = null
                text = stringResource(RCommon.string.chat_payment_status_reclaim_failed)
                color = PolkadotTheme.colors.fg.error
            }

            ChatMessageUiModel.CoinagePayment.Status.ReturnedToSender -> {
                icon = null
                text = stringResource(RCommon.string.chat_payment_status_returned_to_sender)
                color = PolkadotTheme.colors.fg.warning
            }
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PolkadotTheme.spacings.extraTiny)
    ) {
        if (icon != null) {
            PaymentStatusIcon(
                icon = icon,
                color = color,
                spinning = status is ChatMessageUiModel.CoinagePayment.Status.Reclaiming
            )
        }

        NovaText(
            modifier = if (icon == null) {
                Modifier.padding(horizontal = PolkadotTheme.spacings.small)
            } else {
                Modifier
            },
            text = text,
            style = PolkadotTheme.typography.body.medium,
            color = color
        )
    }
}

@Composable
private fun PaymentStatusIcon(
    icon: ImageVector,
    color: Color,
    spinning: Boolean
) {
    val motion = if (spinning) {
        val angle by rememberInfiniteTransition(label = "ReclaimingSpin").animateFloat(
            initialValue = 0f,
            targetValue = -360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = RECLAIMING_SPIN_MILLIS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ReclaimingSpinAngle"
        )

        Modifier
            .rotate(angle)
            .graphicsLayer { scaleX = -1f }
    } else {
        Modifier
    }

    NovaIcon(
        modifier = Modifier
            .size(16.dp)
            .then(motion),
        imageVector = icon,
        tint = color
    )
}

private const val RECLAIMING_SPIN_MILLIS = 1000
