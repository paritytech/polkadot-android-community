package io.paritytech.polkadotapp.feature_settings_impl.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.paritytech.polkadotapp.design.components.icon.NovaIcon
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.AlertOutlined
import io.paritytech.polkadotapp.design.components.icon.vectors.ArrowRight
import io.paritytech.polkadotapp.design.components.icon.vectors.CashOutlined
import io.paritytech.polkadotapp.design.components.icon.vectors.CloudOutlined
import io.paritytech.polkadotapp.design.components.menu.PolkadotMenuListItem
import io.paritytech.polkadotapp.design.components.menu.PolkadotMenuListScope
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageAccountBackupStatus
import io.paritytech.polkadotapp.common.R as RCommon

@Composable
fun PolkadotMenuListScope.SettingsMenuItem(
    onClick: () -> Unit,
    title: String,
    icon: ImageVector? = null,
    label: String? = null
) {
    PolkadotMenuListItem(
        leading = icon?.let { { MenuLeadingIcon(it) } },
        title = { MenuTitle(title) },
        trailing = { MenuValueChevron(label) },
        onClick = onClick
    )
}

@Composable
fun PolkadotMenuListScope.BackupSettingsMenuItem(
    onClick: () -> Unit,
    isBackupMissing: Boolean
) {
    PolkadotMenuListItem(
        leading = { MenuLeadingIcon(NovaIcons.CloudOutlined) },
        title = { MenuTitle(stringResource(RCommon.string.settings_backup)) },
        description = {
            if (isBackupMissing) {
                NovaText(
                    text = stringResource(RCommon.string.settings_backup_not_backed_up),
                    color = PolkadotTheme.colors.fg.warning
                )
            }
        },
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PolkadotTheme.spacings.small)
            ) {
                if (isBackupMissing) {
                    NovaIcon(
                        modifier = Modifier
                            .size(20.dp),
                        imageVector = NovaIcons.AlertOutlined,
                        tint = PolkadotTheme.colors.fg.warning
                    )
                }

                Chevron()
            }
        },
        onClick = onClick
    )
}

@Composable
fun PolkadotMenuListScope.PaymentsSetupMenuItem(status: CoinageAccountBackupStatus) {
    PolkadotMenuListItem(
        leading = { MenuLeadingIcon(NovaIcons.CashOutlined) },
        title = { MenuTitle(stringResource(RCommon.string.settings_payments_setup)) },
        description = { NovaText(text = stringResource(status.descriptionRes())) },
    )
}

private fun CoinageAccountBackupStatus.descriptionRes(): Int = when (this) {
    CoinageAccountBackupStatus.Registering -> RCommon.string.settings_payments_setup_in_progress
    CoinageAccountBackupStatus.Delayed -> RCommon.string.settings_payments_setup_delayed
    CoinageAccountBackupStatus.Completed -> RCommon.string.settings_payments_setup_complete
}

@Composable
private fun BoxScope.MenuLeadingIcon(icon: ImageVector) {
    NovaIcon(
        modifier = Modifier
            .size(20.dp)
            .align(Alignment.Center),
        imageVector = icon,
        tint = PolkadotTheme.colors.fg.secondary
    )
}

@Composable
private fun MenuTitle(text: String) {
    NovaText(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun MenuValueChevron(label: String?) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(PolkadotTheme.spacings.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        label?.let {
            NovaText(
                text = label,
                style = PolkadotTheme.typography.body.mediumEmphasized,
                color = PolkadotTheme.colors.fg.secondary
            )
        }
        Chevron()
    }
}

@Composable
private fun Chevron() {
    NovaIcon(
        modifier = Modifier
            .size(20.dp),
        imageVector = NovaIcons.ArrowRight,
        tint = PolkadotTheme.colors.fg.secondary
    )
}
