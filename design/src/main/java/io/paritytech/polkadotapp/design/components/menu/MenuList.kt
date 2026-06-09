package io.paritytech.polkadotapp.design.components.menu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import io.paritytech.polkadotapp.design.components.spacer.VerticalSpacer
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.theme.PolkadotTheme

private val ContainersSize = 32.dp

@Composable
fun PolkadotMenuList(
    modifier: Modifier = Modifier,
    headerText: String? = null,
    content: @Composable PolkadotMenuListScope.() -> Unit
) {
    val scope = PolkadotMenuListScope().apply { content() }

    Column(modifier = modifier) {
        headerText?.let {
            PolkadotMenuListHeader(
                modifier = Modifier.padding(horizontal = PolkadotTheme.spacings.tiny),
                text = it
            )
            VerticalSpacer { extraMedium }
        }

        scope.items.forEachIndexed { index, itemContent ->
            if (index > 0) VerticalSpacer { extraTiny }

            itemContent(menuItemShape(index = index, total = scope.items.size))
        }
    }
}

class PolkadotMenuListScope internal constructor() {
    internal val items = mutableListOf<@Composable (Shape) -> Unit>()
}

@Composable
fun PolkadotMenuListItem(
    modifier: Modifier = Modifier,
    leading: (@Composable BoxScope.() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    title: @Composable () -> Unit,
    onClick: () -> Unit,
    description: (@Composable () -> Unit)? = null,
) {
    PolkadotMenuListItemInternal(
        modifier = modifier,
        shape = PolkadotTheme.shapes.medium,
        leading = leading,
        trailing = trailing,
        title = title,
        description = description,
        onClick = onClick
    )
}

@Composable
fun PolkadotMenuListScope.PolkadotMenuListItem(
    leading: (@Composable BoxScope.() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    title: @Composable () -> Unit,
    description: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    items += { shape ->
        PolkadotMenuListItemInternal(
            modifier = Modifier,
            shape = shape,
            leading = leading,
            trailing = trailing,
            title = title,
            description = description,
            onClick = onClick
        )
    }
}

@Composable
private fun PolkadotMenuListItemInternal(
    modifier: Modifier,
    shape: Shape,
    leading: (@Composable BoxScope.() -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    title: @Composable () -> Unit,
    description: (@Composable () -> Unit)?,
    onClick: () -> Unit,
) {
    PolkadotSurface(
        modifier = modifier,
        shape = shape,
        color = PolkadotTheme.colors.bg.surface.container,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(PolkadotTheme.spacings.mediumIncreased),
            horizontalArrangement = Arrangement.spacedBy(PolkadotTheme.spacings.mediumIncreased),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading?.let {
                Box(
                    modifier = Modifier.size(ContainersSize),
                ) {
                    it()
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = ContainersSize),
                verticalArrangement = Arrangement.Center
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides PolkadotTheme.colors.fg.primary,
                    LocalTextStyle provides PolkadotTheme.typography.body.large
                ) {
                    title()
                }
                description?.let {
                    CompositionLocalProvider(
                        LocalContentColor provides PolkadotTheme.colors.fg.secondary,
                        LocalTextStyle provides PolkadotTheme.typography.body.smallEmphasized
                    ) {
                        it()
                    }
                }
            }

            trailing?.invoke()
        }
    }
}

@Composable
private fun menuItemShape(index: Int, total: Int): Shape {
    val medium = PolkadotTheme.radii.medium
    val extraSmall = PolkadotTheme.radii.extraSmall
    val isFirst = index == 0
    val isLast = index == total - 1

    return RoundedCornerShape(
        topStart = if (isFirst) medium else extraSmall,
        topEnd = if (isFirst) medium else extraSmall,
        bottomStart = if (isLast) medium else extraSmall,
        bottomEnd = if (isLast) medium else extraSmall
    )
}
