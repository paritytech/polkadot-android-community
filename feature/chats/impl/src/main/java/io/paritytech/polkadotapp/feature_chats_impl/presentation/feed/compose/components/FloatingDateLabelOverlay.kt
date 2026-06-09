package io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_chats_api.presentation.model.ChatDateSeparatorStyle
import kotlinx.coroutines.delay

internal data class StickyHeaderInfo(
    val formattedDate: String,
    val messageIndex: Int // Index of the message whose date group this separator belongs to
)

@Composable
internal fun BoxScope.FloatingDateLabelOverlay(
    stickyHeaderInfo: StickyHeaderInfo?,
    isScrolling: Boolean,
    style: ChatDateSeparatorStyle? = null,
) {
    var stickyHeaderVisible by remember { mutableStateOf(false) }

    LaunchedEffect(isScrolling) {
        if (isScrolling) {
            stickyHeaderVisible = true
        } else {
            delay(1000)
            stickyHeaderVisible = false
        }
    }

    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = PolkadotTheme.spacings.small)
    ) {
        AnimatedVisibility(
            visible = stickyHeaderVisible && stickyHeaderInfo != null,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            stickyHeaderInfo?.let { info ->
                FloatingDateLabel(date = info.formattedDate, style = style)
            }
        }
    }
}

@Composable
private fun FloatingDateLabel(
    modifier: Modifier = Modifier,
    date: String,
    style: ChatDateSeparatorStyle? = null,
) {
    PolkadotSurface(
        modifier = modifier,
        color = style?.backgroundColor ?: PolkadotTheme.colors.bg.surface.container,
        shape = PolkadotTheme.shapes.extraLarge,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            NovaText(
                text = date,
                style = PolkadotTheme.typography.body.mediumEmphasized,
                color = style?.textColor ?: PolkadotTheme.colors.fg.tertiary
            )
        }
    }
}
