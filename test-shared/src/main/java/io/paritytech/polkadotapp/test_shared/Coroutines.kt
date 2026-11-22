package io.paritytech.polkadotapp.test_shared

import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.yield

private const val SETTLE_YIELDS = 5

suspend fun settle() = repeat(SETTLE_YIELDS) { yield() }

suspend fun BaseViewModel.cancelScopeAndJoin() = coroutineContext.job.cancelAndJoin()
