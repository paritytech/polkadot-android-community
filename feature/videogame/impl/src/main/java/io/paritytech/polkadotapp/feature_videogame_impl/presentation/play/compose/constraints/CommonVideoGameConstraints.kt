package io.paritytech.polkadotapp.feature_videogame_impl.presentation.play.compose.constraints

import androidx.constraintlayout.compose.ConstraintSetScope

fun ConstraintSetScope.createCommonVideoGameConstraints() {
    val ref = createRefFor(GameTopBarLayoutId)

    constrain(ref) {
        top.linkTo(parent.top)

        start.linkTo(parent.start)
        end.linkTo(parent.end)
    }
}
