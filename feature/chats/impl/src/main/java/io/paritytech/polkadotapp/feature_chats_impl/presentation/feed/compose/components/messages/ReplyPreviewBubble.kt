package io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.compose.components.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_chats_api.presentation.model.ChatMessageUiModel
import io.paritytech.polkadotapp.feature_chats_api.presentation.model.ReplyPreview
import io.paritytech.polkadotapp.common.R as RCommon

@Composable
fun ReplyPreviewBubble(
    modifier: Modifier,
    preview: ReplyPreview,
    direction: ChatMessageUiModel.Direction,
    onClick: () -> Unit
) {
    val backgroundColor = when (direction) {
        ChatMessageUiModel.Direction.INCOMING -> PolkadotTheme.colors.bg.surface.nested
        ChatMessageUiModel.Direction.OUTGOING -> PolkadotTheme.colors.bg.surface.nestedInverted
    }

    val textColor = when (direction) {
        ChatMessageUiModel.Direction.INCOMING -> PolkadotTheme.colors.fg.primary
        ChatMessageUiModel.Direction.OUTGOING -> PolkadotTheme.colors.fg.primaryInverted
    }

    PolkadotSurface(
        modifier = modifier,
        color = backgroundColor,
        shape = PolkadotTheme.shapes.medium,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        color = PolkadotTheme.colors.stroke.tertiary,
                    )
            )

            Column(
                modifier = Modifier.padding(
                    vertical = PolkadotTheme.spacings.small,
                    horizontal = PolkadotTheme.spacings.tiny
                )
            ) {
                NovaText(
                    text = preview.title,
                    style = PolkadotTheme.typography.title.tinyEmphasized,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // A quoted message with no text is treated as a generic attachment label.
                // Interim: reply previews should later use a sealed type that renders per message kind.
                NovaText(
                    text = preview.text ?: stringResource(RCommon.string.chat_reply_attachment),
                    style = PolkadotTheme.typography.body.smallEmphasized,
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
