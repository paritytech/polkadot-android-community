package io.paritytech.polkadotapp.feature_videogame_impl.presentation.play.compose.constraints

import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstrainedLayoutReference
import androidx.constraintlayout.compose.ConstraintSetScope
import androidx.constraintlayout.compose.Dimension
import io.paritytech.polkadotapp.feature_videogame_impl.presentation.play.compose.referenceId
import io.paritytech.polkadotapp.feature_videogame_impl.presentation.play.compose.sortResult
import io.paritytech.polkadotapp.feature_videogame_impl.presentation.play.models.PlayerUiModel
import kotlinx.collections.immutable.ImmutableList

fun ConstraintSetScope.createHostResetConstraints(
    players: ImmutableList<PlayerUiModel>,
    anchorLeft: ConstrainedLayoutReference,
    anchorRight: ConstrainedLayoutReference
) {
    val guideline = createGuidelineFromStart(0.5f)

    players
        .sortedBy { it.sortResult }
        .forEachIndexed { index, player ->
            val ref = createRefFor(player.referenceId)

            constrain(ref) {
                width = Dimension.fillToConstraints
                height = Dimension.ratio("1:1")

                if (index % 2 == 0) {
                    start.linkTo(parent.start, margin = 8.dp)
                    end.linkTo(guideline, margin = 4.dp)
                } else {
                    start.linkTo(guideline, margin = 4.dp)
                    end.linkTo(parent.end, margin = 8.dp)
                }

                val anchor = if (index % 2 == 0) anchorLeft else anchorRight
                val rowIndex = index / 2

                when (rowIndex) {
                    0 -> {
                        bottom.linkTo(anchor.top, margin = 8.dp)
                    }

                    1 -> {
                        top.linkTo(anchor.top)
                        bottom.linkTo(anchor.bottom)
                    }

                    else -> {
                        top.linkTo(anchor.bottom, margin = 8.dp)
                    }
                }
            }
        }

    hideHostingIntroductionInfo()
    showHostingProgressBar()
    hideHowToPlayButton()
}
