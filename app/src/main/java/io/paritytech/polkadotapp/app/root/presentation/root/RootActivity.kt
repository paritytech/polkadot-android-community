package io.paritytech.polkadotapp.app.root.presentation.root

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import dagger.hilt.android.AndroidEntryPoint
import io.paritytech.polkadotapp.app.BuildConfig
import io.paritytech.polkadotapp.app.R
import io.paritytech.polkadotapp.app.root.navigation.NavigationHolder
import io.paritytech.polkadotapp.app.root.presentation.root.compose.DevResetOverlay
import io.paritytech.polkadotapp.app.root.presentation.root.compose.RootNavBarHost
import io.paritytech.polkadotapp.app.root.presentation.root.compose.chatoverlay.ChatExtensionOverlayHost
import io.paritytech.polkadotapp.common.presentation.deeplink.DeeplinkProcessingOutcome
import io.paritytech.polkadotapp.common.presentation.formatters.time.LocalTimeFormatter
import io.paritytech.polkadotapp.common.presentation.formatters.time.TimeFormatter
import io.paritytech.polkadotapp.common.presentation.notification.AppNotificationHost
import io.paritytech.polkadotapp.common.presentation.notification.AppNotifier
import io.paritytech.polkadotapp.common.presentation.notification.error
import io.paritytech.polkadotapp.common.presentation.resources.ContextManager
import io.paritytech.polkadotapp.common.presentation.screens.ObserveViewModelEvents
import io.paritytech.polkadotapp.common.utils.observe
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import javax.inject.Inject

@AndroidEntryPoint
class RootActivity : AppCompatActivity(R.layout.activity_root) {
    @Inject
    lateinit var navigationHolder: NavigationHolder

    @Inject
    lateinit var contextManager: ContextManager

    @Inject
    lateinit var timeFormatter: TimeFormatter

    @Inject
    lateinit var appNotifier: AppNotifier

    private val viewModel by viewModels<RootViewModel>()

    private val navHostFragment: NavHostFragment by lazy(LazyThreadSafetyMode.NONE) {
        supportFragmentManager.findFragmentById(R.id.rootNavHost) as NavHostFragment
    }

    private val navController: NavController by lazy(LazyThreadSafetyMode.NONE) {
        navHostFragment.navController
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        super.onCreate(savedInstanceState)

        navigationHolder.attach(navController)
        contextManager.attachActivity(this)

        intent?.let(::processIntent)

        handleDeeplinkOutcome()
        if (BuildConfig.DEBUG) {
            setupDevResetOverlay()
        }
        setupRootNavBar()
        setupAppNotificationOverlay()

        setupChatExtensionOverlay()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        processIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()

        contextManager.detachActivity()
        navigationHolder.detach()
    }

    private fun handleDeeplinkOutcome() {
        viewModel.showDeeplinkOutcome.observe { deeplinkProcessingOutcome ->
            when (deeplinkProcessingOutcome) {
                DeeplinkProcessingOutcome.NoOp -> {}

                is DeeplinkProcessingOutcome.ShowMessage -> appNotifier.error(
                    deeplinkProcessingOutcome.message
                )

                is DeeplinkProcessingOutcome.Navigate -> deeplinkProcessingOutcome.navigate()
            }
        }
    }

    private fun setupDevResetOverlay() {
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val showPrompt by viewModel.showDevResetPrompt.collectAsStateWithLifecycle()
                DevResetOverlay(
                    isVisible = showPrompt,
                    onStartOverClick = viewModel::onDevResetStartOverClick,
                    onDismissClick = viewModel::onDevResetDismissClick,
                )
            }
        }
        addContentView(composeView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun setupAppNotificationOverlay() {
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PolkadotTheme {
                    ObserveViewModelEvents(viewModel, appNotifier)

                    AppNotificationHost(notifier = appNotifier)
                }
            }
        }
        addContentView(composeView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    // The global navigation bar: a bottom overlay shown on every screen. Self-contained — it resolves its
    // own view-model and holders; the activity only places it.
    private fun setupRootNavBar() {
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PolkadotTheme {
                    RootNavBarHost(navController = navController)
                }
            }
        }
        addContentView(composeView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun setupChatExtensionOverlay() {
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                CompositionLocalProvider(LocalTimeFormatter provides timeFormatter) {
                    ChatExtensionOverlayHost(
                        navController = navController,
                        overlays = viewModel.chatOverlays,
                        isOnboarded = viewModel.isOnboarded,
                        bottomNavHeight = viewModel.bottomNavHeight,
                    )
                }
            }
        }
        addContentView(composeView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun processIntent(intent: Intent) {
        intent.data?.let {
            viewModel.handleDeepLink(it)
        }
    }
}
