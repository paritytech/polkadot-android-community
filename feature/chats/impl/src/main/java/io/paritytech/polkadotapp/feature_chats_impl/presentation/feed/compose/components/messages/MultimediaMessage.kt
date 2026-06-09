package io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.compose.components.messages

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.ui.compose.ContentFrame
import io.paritytech.polkadotapp.common.presentation.compose.video.VideoPlayerControlsContainer
import io.paritytech.polkadotapp.common.presentation.compose.video.rememberExoPlayer
import io.paritytech.polkadotapp.common.presentation.compose.video.toDefaultMediaSource
import io.paritytech.polkadotapp.common.utils.openImage
import io.paritytech.polkadotapp.design.components.icon.NovaIcon
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.AlertFilled
import io.paritytech.polkadotapp.design.components.image.NovaAsyncImage
import io.paritytech.polkadotapp.design.components.image.rememberBlurHashPlaceholder
import io.paritytech.polkadotapp.design.components.progress.NovaCircularProgressIndicator
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageOrigin
import io.paritytech.polkadotapp.feature_chats_api.presentation.common.getMaxMessageHeight
import io.paritytech.polkadotapp.feature_chats_api.presentation.common.getMaxMessageWidth
import io.paritytech.polkadotapp.feature_chats_api.presentation.model.ChatMessageGrouping
import io.paritytech.polkadotapp.feature_chats_api.presentation.model.ChatMessageSurfaceStyle
import io.paritytech.polkadotapp.feature_chats_api.presentation.model.ChatMessageUiModel
import io.paritytech.polkadotapp.feature_chats_api.presentation.model.ChatMessageUiModel.Multimedia.MultimediaType
import io.paritytech.polkadotapp.feature_chats_api.presentation.model.MessageAction
import io.paritytech.polkadotapp.feature_chats_api.presentation.model.MessageLayoutInfo
import io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.compose.components.icons.MultimediaRetry
import io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.compose.components.icons.VideoPlay
import io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.compose.components.messages.components.FilledMessageTimestamp
import io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.compose.components.messages.components.FlatEditedLabel
import io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.compose.components.messages.components.FlatMessageTimestamp
import io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.compose.components.messages.components.REVEAL_MEDIA_START_DELAY
import io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.compose.components.messages.components.TextMessageLayout
import io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.compose.components.messages.components.rememberRevealedCharCount
import io.paritytech.polkadotapp.feature_chats_impl.presentation.formatter.ChatMessageTimeFormatter
import io.paritytech.polkadotapp.feature_chats_impl.presentation.formatter.LocalChatMessageTimeFormatter
import java.util.UUID
import io.paritytech.polkadotapp.common.R as RCommon

@Composable
fun MultimediaMessage(
    modifier: Modifier,
    message: ChatMessageUiModel.Multimedia,
    showTimestamp: Boolean,
    grouping: ChatMessageGrouping,
    isHighlighted: Boolean,
    onMessageAction: (MessageAction) -> Unit,
    onLongPress: (MessageLayoutInfo) -> Unit,
    customBubbleStyle: ChatMessageSurfaceStyle? = null,
    isRevealing: Boolean = false,
    onRevealComplete: () -> Unit = {},
) {
    val mediaSize = calculateMediaSize(message.type)
    val context = LocalContext.current
    val captionVisibleCharCount = rememberRevealedCharCount(
        fullText = message.text.orEmpty(),
        isRevealing = isRevealing,
        onRevealComplete = onRevealComplete,
        startDelay = REVEAL_MEDIA_START_DELAY,
    )

    ChatMessageContainer(
        modifier = modifier,
        message = message,
        grouping = grouping,
        isHighlighted = isHighlighted,
        canBeReplied = false,
        onMessageAction = onMessageAction,
        onLongPress = onLongPress,
        reactions = message.reactions,
        surfaceStyle = customBubbleStyle ?: ChatMessageSurfaceStyle.default(message.direction),
    ) {
        Column(modifier = Modifier.width(mediaSize.width)) {
            Box {
                when (message.type) {
                    is MultimediaType.Image -> {
                        val imageUri = message.uri
                        // Resource-backed images (e.g. bot drawables) have no external viewer,
                        // so they aren't tappable.
                        val isOpenable =
                            imageUri != null && imageUri.scheme != ContentResolver.SCHEME_ANDROID_RESOURCE
                        ImagePreview(
                            modifier = Modifier
                                .size(mediaSize)
                                .then(
                                    if (isOpenable) {
                                        Modifier.clickable(onClick = { imageUri?.let(context::openImage) })
                                    } else {
                                        Modifier
                                    }
                                ),
                            uri = imageUri,
                            blurHash = message.blurHash
                        )
                    }

                    is MultimediaType.Video -> VideoContent(
                        modifier = Modifier.size(mediaSize),
                        uri = message.uri,
                        blurHash = message.blurHash
                    )
                }

                val uploadState = message.uploadState
                if (uploadState != null) {
                    UploadState(
                        modifier = Modifier.padding(PolkadotTheme.spacings.small),
                        state = uploadState
                    )
                }

                if (message.text == null && showTimestamp) {
                    FilledMessageTimestamp(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(PolkadotTheme.spacings.small),
                        message = message
                    )
                }

                UploadStateControl(
                    modifier = Modifier.align(Alignment.Center),
                    uploadState = message.uploadState
                )
            }

            if (message.text != null) {
                val timestampComposable = if (showTimestamp) {
                    @Composable {
                        FlatMessageTimestamp(message = message)
                    }
                } else {
                    null
                }

                val editedLabelComposable = if (message.isEdited) {
                    @Composable {
                        FlatEditedLabel(direction = message.direction)
                    }
                } else {
                    null
                }

                TextMessageLayout(
                    modifier = Modifier.fillMaxWidth(),
                    text = message.text.orEmpty(),
                    style = PolkadotTheme.typography.body.large,
                    color = customBubbleStyle?.textColor ?: message.direction.defaultTextColor,
                    timestamp = timestampComposable,
                    direction = message.direction,
                    status = editedLabelComposable,
                    visibleCharCount = captionVisibleCharCount,
                )
            }
        }
    }
}

@Composable
private fun ImagePreview(
    modifier: Modifier,
    uri: Uri?,
    blurHash: String?
) {
    val placeholder = rememberBlurHashPlaceholder(blurHash)

    Box(modifier) {
        NovaAsyncImage(
            modifier = Modifier.fillMaxSize(),
            model = uri,
            placeholder = placeholder,
            fallback = placeholder,
            contentScale = ContentScale.Crop
        )

        if (uri == null) {
            NovaCircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun VideoContent(
    modifier: Modifier,
    uri: Uri?,
    blurHash: String?
) {
    var playerAttached by remember(uri) { mutableStateOf(false) }

    Box(modifier) {
        if (playerAttached && uri != null) {
            VideoPlayer(
                modifier = Modifier.fillMaxSize(),
                uri = uri
            )
        } else {
            ImagePreview(
                modifier = Modifier.fillMaxSize(),
                uri = uri,
                blurHash = blurHash
            )

            if (uri != null) {
                PlayButton(
                    modifier = Modifier.align(Alignment.Center),
                    onClick = { playerAttached = true }
                )
            }
        }
    }
}

@Composable
private fun VideoPlayer(
    modifier: Modifier,
    uri: Uri
) {
    val context = LocalContext.current
    val mediaSource = remember(uri) { uri.toDefaultMediaSource(context) }
    val player = rememberExoPlayer(mediaSource = mediaSource, playWhenReady = true)

    VideoPlayerControlsContainer(
        modifier = modifier,
        player = player
    ) {
        ContentFrame(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
            player = player,
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun calculateMediaSize(type: MultimediaType): DpSize {
    val maxWidth = getMaxMessageWidth()
    val maxHeight = getMaxMessageHeight()
    val ratio = type.aspectRatio()

    var width = maxWidth
    var height = maxWidth / ratio

    if (height > maxHeight) {
        height = maxHeight
        width = maxHeight * ratio
    }

    return DpSize(width, height)
}

@Composable
private fun UploadState(
    modifier: Modifier,
    state: ChatMessageUiModel.Multimedia.UploadState
) {
    val backgroundColor: Color
    val text: String

    when (state) {
        is ChatMessageUiModel.Multimedia.UploadState.Uploading -> {
            backgroundColor = PolkadotTheme.colors.bg.surface.overlay
            text = stringResource(RCommon.string.chat_message_multimedia_uploading_progress, state.progressPercent)
        }

        is ChatMessageUiModel.Multimedia.UploadState.Failed -> {
            backgroundColor = PolkadotTheme.colors.bg.status.error
            text = stringResource(RCommon.string.chat_message_multimedia_uploading_failed)
        }
    }

    PolkadotSurface(
        modifier = modifier,
        shape = PolkadotTheme.shapes.full,
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .padding(
                    horizontal = PolkadotTheme.spacings.small,
                    vertical = PolkadotTheme.spacings.extraTiny
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PolkadotTheme.spacings.tiny)
        ) {
            if (state is ChatMessageUiModel.Multimedia.UploadState.Failed) {
                NovaIcon(
                    modifier = Modifier.size(12.dp),
                    imageVector = NovaIcons.AlertFilled,
                    tint = PolkadotTheme.colors.fg.primary
                )
            }

            NovaText(
                text = text,
                style = PolkadotTheme.typography.body.small,
                color = PolkadotTheme.colors.fg.primary
            )
        }
    }
}

@Composable
private fun UploadStateControl(
    modifier: Modifier,
    uploadState: ChatMessageUiModel.Multimedia.UploadState?
) {
    when (uploadState) {
        is ChatMessageUiModel.Multimedia.UploadState.Failed -> {
            Control(
                modifier = modifier,
                onClick = { /* TODO: Handle retry upload */ }
            ) {
                ControlIcon(MultimediaRetry)
            }
        }

        is ChatMessageUiModel.Multimedia.UploadState.Uploading -> {
            Control(modifier = modifier) {
                NovaCircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = PolkadotTheme.colors.fg.primary,
                    trackColor = Color.Transparent,
                )
            }
        }

        null -> Unit
    }
}

@Composable
private fun PlayButton(
    modifier: Modifier,
    onClick: () -> Unit
) {
    Control(
        modifier = modifier,
        onClick = onClick
    ) {
        ControlIcon(VideoPlay)
    }
}

@Composable
private fun ControlIcon(imageVector: ImageVector) {
    NovaIcon(
        modifier = Modifier.size(40.dp),
        imageVector = imageVector,
        tint = PolkadotTheme.colors.fg.primary
    )
}

@Composable
private fun Control(
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    PolkadotSurface(
        modifier = modifier,
        shape = PolkadotTheme.shapes.full,
        color = PolkadotTheme.colors.bg.surface.overlay,
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.size(80.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Preview
@Composable
private fun MultimediaMessagePreview() {
    PolkadotTheme {
        CompositionLocalProvider(
            LocalChatMessageTimeFormatter provides ChatMessageTimeFormatter.mocked()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PolkadotTheme.spacings.mediumIncreased)
                    .verticalScroll(rememberScrollState())
            ) {
                val messageSquareText = ChatMessageUiModel.Multimedia(
                    id = UUID.randomUUID().toString(),
                    timestamp = 0L,
                    direction = ChatMessageUiModel.Direction.INCOMING,
                    status = ChatMessageUiModel.Status.READ,
                    uri = "https://example.com/assets/image.jpg".toUri(),
                    type = MultimediaType.Image(800, 800),
                    text = "Square image with text",
                    blurHash = null,
                    uploadState = null,
                    reactions = listOf(
                        ChatMessageUiModel.Reaction(count = 1, emoji = "❤️", reactedByUser = false),
                        ChatMessageUiModel.Reaction(count = 1, emoji = "❤️", reactedByUser = false),
                    ),
                    origin = ChatMessageOrigin.User,
                    isEdited = false
                )

                MultimediaMessage(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = PolkadotTheme.spacings.mediumIncreased),
                    message = messageSquareText,
                    showTimestamp = true,
                    grouping = ChatMessageGrouping.Standalone,
                    isHighlighted = false,
                    onLongPress = {},
                    onMessageAction = {},
                )

                val messageWideNoText = ChatMessageUiModel.Multimedia(
                    id = UUID.randomUUID().toString(),
                    timestamp = 0L,
                    direction = ChatMessageUiModel.Direction.OUTGOING,
                    status = ChatMessageUiModel.Status.READ,
                    uri = "https://example.com/assets/image.jpg".toUri(),
                    type = MultimediaType.Image(800, 800),
                    text = "This is the text to try the message with text!",
                    blurHash = null,
                    uploadState = ChatMessageUiModel.Multimedia.UploadState.Uploading(10),
                    origin = ChatMessageOrigin.User,
                    reactions = listOf(
                        ChatMessageUiModel.Reaction(count = 1, emoji = "❤️", reactedByUser = false),
                        ChatMessageUiModel.Reaction(count = 1, emoji = "❤️", reactedByUser = false),
                    ),
                    isEdited = false
                )

                MultimediaMessage(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = PolkadotTheme.spacings.mediumIncreased),
                    message = messageWideNoText,
                    showTimestamp = true,
                    grouping = ChatMessageGrouping.Standalone,
                    isHighlighted = false,
                    onLongPress = {},
                    onMessageAction = {},
                )

                val messageSquareNoText = ChatMessageUiModel.Multimedia(
                    id = UUID.randomUUID().toString(),
                    timestamp = 0L,
                    direction = ChatMessageUiModel.Direction.INCOMING,
                    status = ChatMessageUiModel.Status.READ,
                    uri = "https://example.com/assets/image.jpg".toUri(),
                    type = MultimediaType.Image(800, 800),
                    text = null,
                    blurHash = null,
                    uploadState = null,
                    origin = ChatMessageOrigin.User,
                    reactions = listOf(
                        ChatMessageUiModel.Reaction(count = 1, emoji = "❤️", reactedByUser = false),
                        ChatMessageUiModel.Reaction(count = 1, emoji = "❤️", reactedByUser = false),
                    ),
                    isEdited = false
                )

                MultimediaMessage(
                    modifier = Modifier.fillMaxWidth(),
                    message = messageSquareNoText,
                    showTimestamp = true,
                    grouping = ChatMessageGrouping.Standalone,
                    isHighlighted = false,
                    onLongPress = {},
                    onMessageAction = {},
                )
            }
        }
    }
}

private fun MultimediaType.aspectRatio(): Float {
    return when (this) {
        is MultimediaType.Image -> if (width > 0 && height > 0) {
            width.toFloat() / height.toFloat()
        } else {
            1f
        }
        is MultimediaType.Video -> 1f
    }
}
