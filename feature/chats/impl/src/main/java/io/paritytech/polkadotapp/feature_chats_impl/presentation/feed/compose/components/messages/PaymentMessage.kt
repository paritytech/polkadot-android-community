package io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.compose.components.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.paritytech.polkadotapp.common.presentation.paymentAsset.LocalPaymentAssetBrand
import io.paritytech.polkadotapp.common.presentation.paymentAsset.PaymentAssetBrand
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonShape
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonStyle
import io.paritytech.polkadotapp.design.components.button.default.PolkadotButtonSize
import io.paritytech.polkadotapp.design.components.button.default.PolkadotTextButton
import io.paritytech.polkadotapp.design.components.spacer.VerticalSpacer
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageOrigin
import io.paritytech.polkadotapp.feature_chats_api.presentation.common.getMaxMessageWidth
import io.paritytech.polkadotapp.feature_chats_api.presentation.model.ChatMessageGrouping
import io.paritytech.polkadotapp.feature_chats_api.presentation.model.ChatMessageSurfaceStyle
import io.paritytech.polkadotapp.feature_chats_api.presentation.model.ChatMessageUiModel
import io.paritytech.polkadotapp.feature_chats_api.presentation.model.MessageAction
import io.paritytech.polkadotapp.feature_chats_api.presentation.model.MessageLayoutInfo
import io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.compose.components.messages.components.FlatMessageTimestamp
import io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.compose.components.messages.components.PaymentAmounts
import io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.compose.components.messages.components.PaymentStatusIndicator
import io.paritytech.polkadotapp.feature_chats_impl.presentation.formatter.ChatMessageTimeFormatter
import io.paritytech.polkadotapp.feature_chats_impl.presentation.formatter.LocalChatMessageTimeFormatter
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.LocalTokenAmountFormatter
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.TokenAmountFormatter
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.TokenAmountModel
import kotlinx.collections.immutable.persistentListOf
import io.paritytech.polkadotapp.common.R as RCommon

@Composable
fun PaymentMessage(
    modifier: Modifier = Modifier,
    message: ChatMessageUiModel.CoinagePayment,
    grouping: ChatMessageGrouping,
    isHighlighted: Boolean,
    username: String,
    canBeReplied: Boolean,
    onMessageAction: (MessageAction) -> Unit,
    onLongPress: (MessageLayoutInfo) -> Unit,
    customBubbleStyle: ChatMessageSurfaceStyle? = null,
) {
    ChatMessageContainer(
        modifier = modifier,
        message = message,
        grouping = grouping,
        isHighlighted = isHighlighted,
        canBeReplied = canBeReplied,
        onMessageAction = onMessageAction,
        onLongPress = onLongPress,
        reactions = message.reactions,
        surfaceStyle = customBubbleStyle ?: paymentSurfaceStyle(message),
    ) {
        val isIncoming = message.direction == ChatMessageUiModel.Direction.INCOMING
        val headerText = if (isIncoming) {
            val incomingRes = if (message.paymentStatus.isClaimInFlight) {
                RCommon.string.chat_message_payment_incoming_sending
            } else {
                RCommon.string.chat_message_payment_incoming
            }
            stringResource(incomingRes, username)
        } else {
            stringResource(RCommon.string.chat_message_payment_outgoing)
        }

        PaymentMessageContent(
            message = message,
            isIncoming = isIncoming,
            headerText = headerText,
            username = username,
            onReclaim = { onMessageAction(MessageAction.ReclaimPayment(message)) }
        )
    }
}

@Composable
private fun paymentSurfaceStyle(message: ChatMessageUiModel.CoinagePayment): ChatMessageSurfaceStyle {
    val default = ChatMessageSurfaceStyle.default(message.direction)

    return if (message.paymentStatus is ChatMessageUiModel.CoinagePayment.Status.Reclaimed) {
        default.copy(alpha = RECLAIMED_ALPHA)
    } else {
        default
    }
}

@Composable
private fun PaymentMessageContent(
    message: ChatMessageUiModel.CoinagePayment,
    isIncoming: Boolean,
    headerText: String,
    username: String,
    onReclaim: () -> Unit
) {
    val headerColor: Color
    val amountBoxColor: Color
    val primaryTextColor: Color
    val secondaryTextColor: Color

    if (isIncoming) {
        headerColor = PolkadotTheme.colors.fg.primary
        amountBoxColor = PolkadotTheme.colors.bg.surface.nested
        primaryTextColor = PolkadotTheme.colors.fg.primary
        secondaryTextColor = PolkadotTheme.colors.fg.secondary
    } else {
        headerColor = PolkadotTheme.colors.fg.primaryInverted
        amountBoxColor = PolkadotTheme.colors.bg.surface.nestedInverted
        primaryTextColor = PolkadotTheme.colors.fg.primaryInverted
        secondaryTextColor = PolkadotTheme.colors.fg.secondaryInverted
    }

    val differingAmount = message.differingAmount
    val isReclaimed = message.paymentStatus is ChatMessageUiModel.CoinagePayment.Status.Reclaimed
    val isReturnedToSender = message.paymentStatus is ChatMessageUiModel.CoinagePayment.Status.ReturnedToSender

    val bubbleWidth = minOf(PaymentMessageWidth, getMaxMessageWidth())
    val boxHorizontalInset = PolkadotTheme.spacings.extraMedium
    val amountPadding = PolkadotTheme.spacings.mediumIncreased
    val amountMaxWidthPx = with(LocalDensity.current) {
        bubbleWidth.roundToPx() - 2 * boxHorizontalInset.roundToPx() - 2 * amountPadding.roundToPx()
    }

    Column(modifier = Modifier.width(bubbleWidth)) {
        VerticalSpacer { extraMedium }

        NovaText(
            modifier = Modifier
                .padding(horizontal = PolkadotTheme.spacings.extraMedium),
            text = headerText,
            style = PolkadotTheme.typography.body.medium,
            color = headerColor
        )

        PolkadotSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    vertical = PolkadotTheme.spacings.small,
                    horizontal = boxHorizontalInset
                ),
            shape = PolkadotTheme.shapes.medium,
            color = amountBoxColor
        ) {
            Column(
                modifier = Modifier.padding(amountPadding),
                horizontalAlignment = Alignment.Start
            ) {
                PaymentAmounts(
                    amount = message.amount,
                    differingAmount = differingAmount,
                    struckThrough = isReclaimed || isReturnedToSender,
                    maxWidthPx = amountMaxWidthPx,
                    primaryTextColor = primaryTextColor,
                    secondaryTextColor = secondaryTextColor
                )
            }
        }

        PaymentStatusIndicator(
            modifier = Modifier.padding(horizontal = PolkadotTheme.spacings.extraMedium + PolkadotTheme.spacings.tiny),
            status = message.paymentStatus,
            baseStatusColor = secondaryTextColor,
            isIncoming = isIncoming,
            username = username,
            amount = message.amount,
            differingAmount = differingAmount
        )

        if (!isIncoming && message.paymentStatus.canReclaim) {
            VerticalSpacer { small }

            PolkadotTextButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PolkadotTheme.spacings.extraMedium),
                text = stringResource(
                    if (message.paymentStatus is ChatMessageUiModel.CoinagePayment.Status.ReclaimFailed) {
                        RCommon.string.chat_payment_reclaim_retry_action
                    } else {
                        RCommon.string.chat_payment_reclaim_action
                    }
                ),
                style = PolkadotButtonStyle.secondaryInverted(),
                size = PolkadotButtonSize.medium(),
                shape = PolkadotButtonShape.rounded,
                onClick = onReclaim
            )
        }

        VerticalSpacer { tiny }

        Box(
            modifier = Modifier
                .padding(horizontal = PolkadotTheme.spacings.small)
                .align(Alignment.End)
        ) {
            if (isIncoming) {
                FlatMessageTimestamp(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    message = message
                )
            } else {
                FlatMessageTimestamp(message = message)
            }
        }

        VerticalSpacer { small }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = PREVIEW_HEIGHT)
@Composable
private fun IncomingMessagesPreview() {
    MessagesPreview(direction = ChatMessageUiModel.Direction.INCOMING)
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = PREVIEW_HEIGHT)
@Composable
private fun OutcomingMessagesPreview() {
    MessagesPreview(direction = ChatMessageUiModel.Direction.OUTGOING)
}

@Composable
private fun MessagesPreview(direction: ChatMessageUiModel.Direction) {
    PolkadotTheme {
        CompositionLocalProvider(
            LocalChatMessageTimeFormatter provides ChatMessageTimeFormatter.mocked(),
            LocalTokenAmountFormatter provides TokenAmountFormatter.mocked,
            LocalPaymentAssetBrand provides PaymentAssetBrand.mocked
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                listOf(20, 20_000, 999_999, 2_000_000).forEach { value ->
                    PaymentMessagePreview(
                        direction = direction,
                        amount = TokenAmountModel.mock(value = value),
                        paymentStatus = ChatMessageUiModel.CoinagePayment.Status.Detected(TokenAmountModel.mock(value = value)),
                    )
                }
                PaymentMessagePreview(
                    direction = direction,
                    paymentStatus = ChatMessageUiModel.CoinagePayment.Status.Detecting,
                )
                PaymentMessagePreview(
                    direction = direction,
                    paymentStatus = ChatMessageUiModel.CoinagePayment.Status.Detected(TokenAmountModel.mock),
                )
                PaymentMessagePreview(
                    direction = direction,
                    paymentStatus = ChatMessageUiModel.CoinagePayment.Status.PartiallyClaimed(
                        TokenAmountModel.mock(value = 500)
                    ),
                )
                PaymentMessagePreview(
                    direction = direction,
                    paymentStatus = ChatMessageUiModel.CoinagePayment.Status.Transferred(TokenAmountModel.mock),
                )
                // Claiming ended short: the amount is struck through and the shortfall called out. Only a
                // finished claim reaches this, which is what keeps it off the screen while a retry is due.
                PaymentMessagePreview(
                    direction = direction,
                    paymentStatus = ChatMessageUiModel.CoinagePayment.Status.Transferred(TokenAmountModel.mock(value = 500)),
                )
                PaymentMessagePreview(
                    direction = direction,
                    paymentStatus = ChatMessageUiModel.CoinagePayment.Status.FailedDetection,
                )
                PaymentMessagePreview(
                    direction = direction,
                    paymentStatus = ChatMessageUiModel.CoinagePayment.Status.FailedTransfer,
                )
                PaymentMessagePreview(
                    direction = direction,
                    paymentStatus = ChatMessageUiModel.CoinagePayment.Status.Detected(TokenAmountModel.mock),
                    deliveryStatus = ChatMessageUiModel.Status.FAILED,
                )
                if (direction == ChatMessageUiModel.Direction.INCOMING) {
                    PaymentMessagePreview(
                        direction = direction,
                        paymentStatus = ChatMessageUiModel.CoinagePayment.Status.ReturnedToSender,
                    )
                }
                if (direction == ChatMessageUiModel.Direction.OUTGOING) {
                    PaymentMessagePreview(
                        direction = direction,
                        paymentStatus = ChatMessageUiModel.CoinagePayment.Status.Unclaimed,
                    )
                    PaymentMessagePreview(
                        direction = direction,
                        paymentStatus = ChatMessageUiModel.CoinagePayment.Status.Reclaiming,
                    )
                    PaymentMessagePreview(
                        direction = direction,
                        paymentStatus = ChatMessageUiModel.CoinagePayment.Status.Reclaimed(TokenAmountModel.mock),
                    )
                    PaymentMessagePreview(
                        direction = direction,
                        paymentStatus = ChatMessageUiModel.CoinagePayment.Status.ReclaimFailed,
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentMessagePreview(
    direction: ChatMessageUiModel.Direction,
    paymentStatus: ChatMessageUiModel.CoinagePayment.Status,
    amount: TokenAmountModel = TokenAmountModel.mock,
    deliveryStatus: ChatMessageUiModel.Status = ChatMessageUiModel.Status.SENT
) {
    PaymentMessage(
        modifier = Modifier.fillMaxWidth(),
        message = ChatMessageUiModel.CoinagePayment(
            id = "1",
            direction = direction,
            status = deliveryStatus,
            timestamp = System.currentTimeMillis(),
            amount = amount,
            paymentStatus = paymentStatus,
            origin = ChatMessageOrigin.User,
            reactions = persistentListOf()
        ),
        grouping = ChatMessageGrouping.Standalone,
        isHighlighted = false,
        username = "Glak",
        canBeReplied = true,
        onMessageAction = {},
        onLongPress = {}
    )
}

private val PaymentMessageWidth = 250.dp

private const val RECLAIMED_ALPHA = 0.55f

/** Tall enough for the whole status gallery; the scroll is for the interactive preview. */
private const val PREVIEW_HEIGHT = 3000
