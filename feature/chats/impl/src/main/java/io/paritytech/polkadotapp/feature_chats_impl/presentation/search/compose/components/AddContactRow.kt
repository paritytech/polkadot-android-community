package io.paritytech.polkadotapp.feature_chats_impl.presentation.search.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import io.paritytech.polkadotapp.design.components.avatar.AvatarUiModel
import io.paritytech.polkadotapp.design.components.avatar.PolkadotAvatar
import io.paritytech.polkadotapp.design.components.docklet.DockletTypography
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_chats_impl.presentation.chatSearch.compose.components.ChatSearchPersonAvatarSize

@Composable
internal fun AddContactRow(
    title: String,
    avatarModel: AvatarUiModel,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = PolkadotTheme.spacings.extraMedium),
        horizontalArrangement = Arrangement.spacedBy(PolkadotTheme.spacings.extraMedium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PolkadotAvatar(
            modifier = Modifier.size(ChatSearchPersonAvatarSize),
            model = avatarModel,
            initialStyle = DockletTypography.AvatarInitial,
            initialColor = PolkadotTheme.colors.fg.primary,
        )
        NovaText(
            modifier = Modifier.weight(1f),
            text = title,
            style = DockletTypography.RowTitle,
            color = PolkadotTheme.colors.fg.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
